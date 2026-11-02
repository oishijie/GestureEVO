package io.github.lumiagg.gestureevo.settings

import io.github.lumiagg.gestureevo.db.APPModel
import org.junit.Assert.*
import org.junit.Test

class AppSelectionFlagsTest {
    @Test fun togglingBlacklistDoesNotChangeSystemOrLauncherClassification() {
        val app = APPModel().apply {
            setState(APPModel.FLAG_SYSTEM, true)
            setState(APPModel.FLAG_LAUNCHER, true)
        }
        app.reverseState(APPModel.FLAG_GESTURE_DISABLE)
        assertTrue(app.getState(APPModel.FLAG_GESTURE_DISABLE))
        assertTrue(app.getState(APPModel.FLAG_SYSTEM))
        assertTrue(app.getState(APPModel.FLAG_LAUNCHER))
        app.reverseState(APPModel.FLAG_GESTURE_DISABLE)
        assertFalse(app.getState(APPModel.FLAG_GESTURE_DISABLE))
        assertTrue(app.getState(APPModel.FLAG_SYSTEM))
    }

    @Test fun notificationWhitelistAndIconBlacklistRemainIndependent() {
        val app = APPModel()
        app.setState(APPModel.FLAG_NOTIFICATION_APPS_ENABLE, true)
        app.setState(APPModel.FLAG_NOTIFICATION_ICON_DISABLE, true)
        app.setState(APPModel.FLAG_NOTIFICATION_APPS_ENABLE, false)
        assertFalse(app.getState(APPModel.FLAG_NOTIFICATION_APPS_ENABLE))
        assertTrue(app.getState(APPModel.FLAG_NOTIFICATION_ICON_DISABLE))
    }
}
