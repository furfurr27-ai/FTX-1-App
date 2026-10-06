# NEXT ACTION — FTX-1 FieldOps

**CP-0005B — Manual-QSO LoTW queue**

Add a logger policy that, when enabled, automatically places successfully saved local SSB/CW and eligible digital QSOs into the existing `LotwUploadQueue` by immutable QSO id.

Local logging remains authoritative and must succeed independently of network/LoTW status. Disabled policy leaves the QSO NOT_UPLOADED. Do not enable real automatic network upload; CP-0003C remains hardware/account gated.
