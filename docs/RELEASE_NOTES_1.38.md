# Easy Health 1.38

## Changes
- Added manual device registration entries for OMRON HEM-7140T1 and CAS BFA-S12.
- Added a baseline body-weight field to each person profile. Existing profiles without this field remain readable.
- Added independent 1-week, 1-month, 3-month, and all-time report periods for each vital section.
- Updated BLE characteristic notification handling for Android 13+ and delayed the final import snapshot after the Glucose Service RACP completion response.
- Glucose import now reports the number of records received, the newest timestamped reading, and whether that latest reading is present in the saved record set.

## Hardware integration status
OMRON HEM-7140T1 and CAS BFA-S12 can be registered and their vital types can be recorded in this build. Direct Easy Health BLE data reception for these two devices is not enabled yet; their GATT data formats and real-device behavior must be verified before enabling automatic import.

## Validation
The GitHub Actions workflow checks unit tests, APK assembly, application ID, version code/name, and the established signing certificate. Physical-device testing remains necessary for Bluetooth transfers and the newest glucose reading.
