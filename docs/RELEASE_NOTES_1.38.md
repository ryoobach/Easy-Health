# Easy Health 1.38

## Measurement profiles
- Require each measurement subject to provide a name, sex, age, and height before the profile can be saved.
- Do not ask for a baseline weight in the profile. Weight remains a dated measurement record and can change over time.
- Use sex, age, and height as context for individual health interpretation and BMI calculations based on recorded weight.

## Calendar, appointments, journal, and notes
- Rename the calendar tab to **일정·일기** and show records for the selected date.
- Keep new-record actions above the existing date-specific record cards.
- Support multiple hospital appointments, health-journal entries, and simple memos on the same date.
- Open an existing card in edit mode.
- Display the appointment status label as **확인**.
- Require a reminder choice for hospital appointments (one day before, two hours before, 30 minutes before, or one day plus two hours).
- Health-journal entries have no reminder. Simple memos have optional reminders.

## Device registration and measurement import
- The device-add dialog separates registered devices from newly discovered Bluetooth devices.
- Registered devices have no checkboxes; long-pressing one opens a delete confirmation. Removing a device does not delete historical measurement records.
- Newly discovered supported devices appear with checkboxes and are registered only after pressing the add button.
- In the Records tab, keep the Bluetooth icon and label the action **측정값 자동 가져오기**.
- Contour Plus ONE and CareSens Dual remain the two glucose meters exposed for automatic glucose import. OMRON HEM-7140T1 and CAS BFA-S12 can be registered, but automatic import remains disabled until their device protocols are verified.

## Validation and test scope
- The isolated test workflow runs unit tests, assembles the debug APK, verifies application ID/version, verifies the test signing certificate, packages the APK and installation guide, and uploads the artifact.
- Test application ID: `com.ryoo.easyhealth.test`; version code: `38`; version name: `1.38`; launcher label: `Easy Health 1.38 TEST`.
- The existing production package `com.ryoo.easyhealth` at version 1.37 is not modified or replaced by this isolated test build.
- CI cannot verify physical Bluetooth transfers; Contour Plus ONE and CareSens Dual still require real-device pairing/import validation on the phone.
