package com.example.myapplication.ui.navigation

interface NavigationDestination {

    /**
     * Nombre único para identificar esta pantalla en la pantalla de navegación.
     */
    val route: String

    /**
     * Recurso de cadena para el título de la pantalla en la barra de acción
     */

    val titleRes: Int
}