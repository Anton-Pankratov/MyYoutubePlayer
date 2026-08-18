package kg.dev.shared.feature.history

import com.arkivanov.decompose.ComponentContext
import kg.dev.shared.core.storage.db.PlayerDatabase
import kg.dev.shared.feature.history.data.DefaultHistoryRepository
import kg.dev.shared.feature.history.data.SqlDelightHistoryDataSource
import kg.dev.shared.feature.history.domain.HistoryRepository
import kg.dev.shared.feature.history.presentation.DefaultHistoryComponent
import kg.dev.shared.feature.history.presentation.HistoryComponent
import org.koin.dsl.module

val historyFeatureModule = module {
    single { SqlDelightHistoryDataSource(get<PlayerDatabase>()) }
    single<HistoryRepository> { DefaultHistoryRepository(get()) }
    factory<HistoryComponent> { (componentContext: ComponentContext) ->
        DefaultHistoryComponent(componentContext, get())
    }
}
