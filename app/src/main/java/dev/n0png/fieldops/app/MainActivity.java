package dev.n0png.fieldops.app;

/**
 * FieldOps Android launcher. The integrated FieldOpsOperatingActivity
 * deliberately does not connect to a radio, start audio, key PTT,
 * transmit RF or submit to LoTW. Actual mode/data features remain
 * visibly gated until their own software and physical acceptance.
 *
 * Keeping the explicit launcher name preserves the tested Android
 * entry point and the existing private archive Activity.
 */
public final class MainActivity extends FieldOpsOperatingActivity {
}
