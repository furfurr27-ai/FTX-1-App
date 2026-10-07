package dev.n0png.fieldops.core.propagation

data class PropagationRefreshWorkspaceResult(
    val refresh: PropagationRefreshCycleResult,
    val projection: PropagationWorkspaceProjection?,
)

class PropagationRefreshWorkspaceService(
    private val refreshCoordinator: PropagationSourceRefreshCoordinator,
    private val projectionService: PropagationWorkspaceProjectionService,
) {
    fun refreshAndProject(
        query: PropagationProjectionQuery,
    ): PropagationRefreshWorkspaceResult {
        val refresh = refreshCoordinator.refresh(query.nowUtcMillis)
        val projection = projectionService.latest(query)
        return PropagationRefreshWorkspaceResult(
            refresh = refresh,
            projection = projection,
        )
    }
}
