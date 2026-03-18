package co.lgcs.telewatch

import android.app.Application
import org.telegram.messenger.ApplicationLoader

/**
 * Initialises the Telegram core (MTProto, storage, accounts) then hands off.
 * We piggyback on ApplicationLoader which sets up everything needed by
 * MessagesController, ConnectionsManager, etc.
 */
class WearApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Delegate to the existing Telegram application bootstrap.
        // This brings up MTProto connections, account instances, push, storage.
        ApplicationLoader.applicationContext = this
        ApplicationLoader.postInitApplication()
    }
}
