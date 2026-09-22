package eu.depau.loak.domain.repositories

import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.domain.manager.SessionManager

class ShareRepository(
	private val sessionManager: SessionManager
) {
	suspend fun getShares() = sessionManager.api.getShares().map { it.toDomainModel() }
}
