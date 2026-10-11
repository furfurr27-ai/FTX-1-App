package dev.n0png.fieldops.app;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.Shadows;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ArchiveHistoryActivityTest {
    private static List<Button> buttons(View root, String name) {
        List<Button> found = new ArrayList<>();
        collectButtons(root, name, found);
        return found;
    }
    private static void collectButtons(View root, String name, List<Button> output) {
        if (root instanceof Button && ((Button) root).getText().toString().equals(name)) {
            output.add((Button) root);
        }
        if (root instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) root;
            for (int i = 0; i < parent.getChildCount(); i++) {
                collectButtons(parent.getChildAt(i), name, output);
            }
        }
    }
    private static void click(ArchiveHistoryActivity activity, String name, int index) {
        List<Button> buttons = buttons(activity.getWindow().getDecorView(), name);
        assertTrue("Missing clickable action " + name + " index " + index,
                   buttons.size() > index);
        assertTrue("Click rejected " + name, buttons.get(index).performClick());
    }
    private static String visibleText(View root) {
        StringBuilder out = new StringBuilder();
        collectText(root, out);
        return out.toString();
    }
    private static void collectText(View view, StringBuilder out) {
        if (view instanceof TextView) {
            out.append(((TextView) view).getText()).append('\n');
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectText(group.getChildAt(i), out);
            }
        }
    }
    private static String screen(ArchiveHistoryActivity activity) {
        return visibleText(activity.getWindow().getDecorView());
    }

    @Test
    public void canonicalHistoryActionsAndWarningsRemainInActualActivity() {
        try (ActivityController<ArchiveHistoryActivity> controller =
                 Robolectric.buildActivity(ArchiveHistoryActivity.class).setup()) {
            ArchiveHistoryActivity activity = controller.get();
            assertTrue(screen(activity).contains("Retained reports: 3"));
            assertTrue(screen(activity).contains("NOT LIVE"));
            assertTrue(screen(activity).contains("SHA-256 identifies content"));
            assertEquals(2, buttons(activity.getWindow().getDecorView(), "SELECT").size());
            click(activity, "NEXT PAGE", 0);
            assertTrue(screen(activity).contains("Archived reports 3 through 3 of 3"));
            click(activity, "PREVIOUS PAGE", 0);
            click(activity, "SELECT", 0);
            assertTrue(screen(activity).contains("SELECTED HISTORY"));
            click(activity, "SET BEFORE", 0);
            click(activity, "SET AFTER", 1);
            click(activity, "COMPARE CANONICAL ORIGINALS", 0);
            assertTrue(screen(activity).contains("HISTORICAL COMPARISON"));
            assertTrue(screen(activity).contains("Both canonical originals"));
            click(activity, "CLEAR COMPARISON", 0);
            assertFalse(screen(activity).contains("HISTORICAL COMPARISON"));
            click(activity, "DELETE", 0);
            assertTrue(screen(activity).contains("Retained reports: 2"));
        }
    }

    @Test
    public void rotationRetainsOnlyInMemorySessionButFreshProcessDoesNot() {
        ActivityController<ArchiveHistoryActivity> controller =
            Robolectric.buildActivity(ArchiveHistoryActivity.class).setup();
        ArchiveHistoryActivity activity = controller.get();
        click(activity, "ADD SYNTHETIC REPORT", 0);
        assertTrue(screen(activity).contains("Retained reports: 4"));
        controller.configurationChange();
        activity = controller.get();
        assertTrue("Rotation must retain transient archive only",
                   screen(activity).contains("Retained reports: 4"));
        controller.pause().stop().destroy();

        try (ActivityController<ArchiveHistoryActivity> fresh =
                 Robolectric.buildActivity(ArchiveHistoryActivity.class).setup()) {
            assertTrue("Fresh launch must not fabricate Android persistence",
                       screen(fresh.get()).contains("Retained reports: 3"));
        }
    }

    @Test
    public void explicitOptInSaveAndFreshProcessRestoreRemainSeparateUserActions() {
        try (ActivityController<ArchiveHistoryActivity> first =
                 Robolectric.buildActivity(ArchiveHistoryActivity.class).setup()) {
            ArchiveHistoryActivity activity = first.get();
            assertEquals(0, buttons(activity.getWindow().getDecorView(),
                "SAVE SYNTHETIC ARCHIVE LOCALLY").size());
            click(activity, "ENABLE LOCAL ARCHIVE STORAGE", 0);
            click(activity, "DELETE LOCAL ARCHIVE FILE", 0);
            click(activity, "ADD SYNTHETIC REPORT", 0);
            assertTrue(screen(activity).contains("Retained reports: 4"));
            click(activity, "SAVE SYNTHETIC ARCHIVE LOCALLY", 0);
            assertTrue(screen(activity).contains("Saved canonical synthetic reports"));
            click(activity, "DISABLE LOCAL ARCHIVE STORAGE", 0);
            assertEquals(0, buttons(activity.getWindow().getDecorView(),
                "RESTORE SAVED LOCAL ARCHIVE").size());
        }
        try (ActivityController<ArchiveHistoryActivity> fresh =
                 Robolectric.buildActivity(ArchiveHistoryActivity.class).setup()) {
            ArchiveHistoryActivity activity = fresh.get();
            assertTrue("Opt-in is not carried to a fresh Activity",
                screen(activity).contains("storage is DISABLED"));
            assertTrue(screen(activity).contains("Retained reports: 3"));
            click(activity, "ENABLE LOCAL ARCHIVE STORAGE", 0);
            click(activity, "RESTORE SAVED LOCAL ARCHIVE", 0);
            assertTrue(screen(activity).contains("Retained reports: 4"));
            assertTrue(screen(activity).contains("Restored 4 canonical offline reports"));
            click(activity, "DELETE LOCAL ARCHIVE FILE", 0);
            click(activity, "RESTORE SAVED LOCAL ARCHIVE", 0);
            assertTrue(screen(activity).contains("No saved local report archive exists."));
        }
    }

    @Test
    public void launcherCanNavigateToPrivateSyntheticHistoryScreen() {
        try (ActivityController<MainActivity> mainController =
                 Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity main = mainController.get();
            List<Button> open = buttons(main.getWindow().getDecorView(),
                "OPEN SYNTHETIC OFFLINE ARCHIVE HISTORY");
            assertEquals(1, open.size());
            assertTrue(open.get(0).performClick());
            Intent next = Shadows.shadowOf(main).getNextStartedActivity();
            assertNotNull(next);
            assertEquals(ArchiveHistoryActivity.class.getName(),
                next.getComponent().getClassName());
        }
    }
}
