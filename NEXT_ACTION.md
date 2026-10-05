# NEXT ACTION — FTX-1 FieldOps

**CP-0002E — Native-mode regression**

Run the FT8/FT4/FT2, JS8, WSPR and APRS regression families separately. Keep their gates independent, verify the shared audio/timing/TX-ownership composition, and create one immutable native-modes checkpoint only after every required family is green.

Do **not** begin CP-0003A TrustedQSL signer integration until CP-0002E is durable. Android/FTX-1 device and RF status remain separately hardware-gated.
