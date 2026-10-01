package eu.depau.loak.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(config: KoinAppDeclaration? = null): KoinApplication {
	return startKoin {
		config?.invoke(this)
		allowOverride(true)
		printLogger()
		modules(
			appModule,
			databaseModule,
			managerModule,
			repositoryModule,
			viewModelModule,
			platformModule
		)
		koin.createEagerInstances()
	}
}
