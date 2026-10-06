# NEXT ACTION — FTX-1 FieldOps

**CP-0005A — Universal QSO model + fast logger**

Implement and host-verify the software-only logging checkpoint:

- universal QSO record with exact mode/submode, radio mode, band/frequency, UTC, callsign, RST, grid/location, station profile and operating session
- fast manual SSB/CW logging from current session/radio context
- digital completed-contact auto-log adapters into the same QSO model
- ADIF export preserving exact mode/submode and frequency
- no automatic LoTW enqueue yet; that remains CP-0005B

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain explicitly deferred and incomplete.
