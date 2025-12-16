package me.sdi.ethwallet.ui.navigation

class Navigator(
    private val navigationState: NavigationState,
) {
    fun navigate(destination: Destination) {
        navigationState.backStack.add(destination)
    }

    fun goBack(): Boolean {
        return if (navigationState.backStack.isNotEmpty()) {
            navigationState.backStack.removeLastOrNull()
            true
        } else {
            false
        }
    }

    fun navigateAndClearStack(destination: Destination) {
        navigationState.backStack.clear()
        navigationState.backStack.add(destination)
    }
}
