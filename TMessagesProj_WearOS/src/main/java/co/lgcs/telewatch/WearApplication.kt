package co.lgcs.telewatch

import android.app.Application
import org.telegram.messenger.ApplicationLoader

/**
 * Initialises the Telegram core (MTProto, storage, accounts) then hands off.
 * We piggyback on ApplicationLoader which sets up everything needed by
 * MessagesController, ConnectionsManager, etc.
 */
class WearApplication : ApplicationLoader() {

    override fun onCreate() {
        super.onCreate()
    }
}
