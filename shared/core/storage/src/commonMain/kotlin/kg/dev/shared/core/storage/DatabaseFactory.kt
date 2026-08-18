package kg.dev.shared.core.storage

import app.cash.sqldelight.db.SqlDriver
import kg.dev.shared.core.storage.db.PlayerDatabase

/** Platform composition roots own the driver lifecycle and inject it here. */
fun createPlayerDatabase(driver: SqlDriver): PlayerDatabase = PlayerDatabase(driver)
