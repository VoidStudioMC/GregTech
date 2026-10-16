# Data stick handlers

Data sticks, data orbs and data modules use `DataStickRegistry` to store one active payload. Register a
`DataStickHandler<T>` under a stable string ID during initialization on **both client and server**. Keep the returned
handler instance for typed reads and writes; registering a duplicate ID or reusing an instance under another ID fails.

```java
public static final DataStickHandler<String> NOTE = DataStickRegistry.register("myaddon:note",
        new DataStickHandler<String>() {
            @Override
            public NBTTagCompound serializeNBT(String text) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("Text", text);
                return tag;
            }

            @Override
            public String deserializeNBT(NBTTagCompound tag) {
                return tag.hasKey("Text", Constants.NBT.TAG_STRING) ? tag.getString("Text") : null;
            }

            @Override
            public String getDisplayName(String text) {
                return LocalizationUtils.format("myaddon.data_stick.note.name");
            }

            @Override
            public void addInformation(String text, List<String> lines) {
                lines.add(text);
            }
        });

// Write when handling a server-side interaction. This replaces the previous handler and payload.
NOTE.write(dataStick, "Saved note");

// Null means another handler owns the item, there is no data, or decoding failed.
String text = NOTE.read(dataStick);
```

The API classes are in `gregtech.api.items.datastick`. The example also uses Minecraft's `NBTTagCompound`, Forge's
`Constants`, `java.util.List` and GregTech's `LocalizationUtils`.

`serializeNBT` must return a compound. `deserializeNBT` receives a copy and should return `null` for unsupported or
malformed payloads. The registry also copies serialized data, so later changes to the input or decoded object cannot
change the item. A serialization failure leaves the original item unchanged.

`getDisplayName` may return `null` to use the normal item name. It can be called on the server, so use
`LocalizationUtils` for translations there. `addInformation` appends client tooltip lines to the item's standard
description. Name and tooltip callbacks receive the decoded data. Unknown handler IDs or invalid envelopes fall back
to the normal name and description, leaving the stored bytes intact. As with other items, an anvil name applied after
writing takes precedence over the handler's name.

The item tag stores the ID separately from the handler's payload:

```snbt
{
  "GT.DataStick": {
    Handler: "myaddon:note",
    Data: { Text: "Saved note" }
  }
}
```

Each write replaces this entire entry, clears old `display.Name` / `display.LocName` values and removes registered
legacy payload tags. Other item metadata is preserved. The `write(NBTTagCompound, T)` overload applies the same rules
to an existing item tag. The API does not check item type, allowing addons to attach `DataItemBehavior` to their own
data items.

GregTech registers these built-in handlers in `DataStickHandlers`:

| ID | Payload | Legacy top-level compound |
| --- | --- | --- |
| `gregtech:assembly_line` | Research ID | `assemblylineResearch` |
| `gregtech:me_input_bus` | Item slots, ghost circuit, optional auto-pull flag | `MEInputBus` |
| `gregtech:me_input_hatch` | Fluid slots, optional auto-pull flag | `MEInputHatch` |

Existing items remain readable without modifying NBT when displaying or applying their data. New writes use the new
format. Stocking buses and hatches share the respective ME handler and retain their `AutoPull` setting. ME tooltips
show saved slot contents and quantities, circuit configuration and auto-pull state.

`AssemblyLineManager.writeResearchToNBT`, `readResearchId` and `hasResearchTag` use the research handler, so recipes,
JEI, research stations and data access hatches work with both formats. Scanner copying preserves the complete payload,
including unknown addon IDs, and retains the destination item's Data Bank requirement.

For an addon's former top-level compound, call `DataStickRegistry.registerLegacyTag("OldTag", NOTE)` after registering
the handler, provided its codec can decode that compound. Legacy tags are considered only when `GT.DataStick` is
absent. They are checked in registration order (ME bus, ME hatch, then research for built-ins). Even an unknown or
malformed new-format entry suppresses legacy fallback, so stale data cannot become active again.
