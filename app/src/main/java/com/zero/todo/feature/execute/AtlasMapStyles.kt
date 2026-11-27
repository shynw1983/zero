package com.zero.todo.feature.execute

/**
 * Minimalist Swiss-architectural Google Maps styles paired with ZERO's Night Slate and Cool Paper palettes.
 * Commercial POIs and visual clutter are suppressed so 3D buildings and quest beacons dominate cleanly.
 */
object AtlasMapStyles {

    // Brighter Tactical Midnight Slate so 3D buildings, streets, water, and labels remain crisp in dark mode.
    val OBSIDIAN_DARK_STYLE_JSON: String = """
        [
          {"elementType":"geometry","stylers":[{"color":"#181D27"}]},
          {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
          {"elementType":"labels.text.fill","stylers":[{"color":"#AEB9CC"}]},
          {"elementType":"labels.text.stroke","stylers":[{"color":"#141923"}]},
          {"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#3A4354"}]},
          {"featureType":"landscape.man_made","elementType":"geometry.fill","stylers":[{"color":"#273042"}]},
          {"featureType":"landscape.man_made","elementType":"geometry.stroke","stylers":[{"color":"#3D4A63"}]},
          {"featureType":"poi","stylers":[{"visibility":"off"}]},
          {"featureType":"poi.park","elementType":"geometry","stylers":[{"visibility":"on"},{"color":"#1B2E28"}]},
          {"featureType":"road","elementType":"geometry.fill","stylers":[{"color":"#303A4F"}]},
          {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#161B24"}]},
          {"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#9BA7BC"}]},
          {"featureType":"road.arterial","elementType":"geometry.fill","stylers":[{"color":"#3D4B66"}]},
          {"featureType":"road.highway","elementType":"geometry.fill","stylers":[{"color":"#4F6182"}]},
          {"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#222B3C"}]},
          {"featureType":"transit","elementType":"geometry","stylers":[{"color":"#273145"}]},
          {"featureType":"transit.station","elementType":"labels.text.fill","stylers":[{"color":"#D4DEEE"}]},
          {"featureType":"water","elementType":"geometry","stylers":[{"color":"#112A42"}]},
          {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#5B7A99"}]}
        ]
    """.trimIndent()

    val COOL_PAPER_LIGHT_STYLE_JSON: String = """
        [
          {"elementType":"geometry","stylers":[{"color":"#F3F4F7"}]},
          {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
          {"elementType":"labels.text.fill","stylers":[{"color":"#6B7280"}]},
          {"elementType":"labels.text.stroke","stylers":[{"color":"#FFFFFF"}]},
          {"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#D4D4D8"}]},
          {"featureType":"landscape.man_made","elementType":"geometry.fill","stylers":[{"color":"#E6E8EE"}]},
          {"featureType":"landscape.man_made","elementType":"geometry.stroke","stylers":[{"color":"#CFD3DC"}]},
          {"featureType":"poi","stylers":[{"visibility":"off"}]},
          {"featureType":"poi.park","elementType":"geometry","stylers":[{"visibility":"on"},{"color":"#E4ECE8"}]},
          {"featureType":"road","elementType":"geometry.fill","stylers":[{"color":"#FFFFFF"}]},
          {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#E2E4EA"}]},
          {"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#71717A"}]},
          {"featureType":"road.arterial","elementType":"geometry.fill","stylers":[{"color":"#FAFAFC"}]},
          {"featureType":"road.highway","elementType":"geometry.fill","stylers":[{"color":"#E5E8F0"}]},
          {"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#CBD0DC"}]},
          {"featureType":"transit","elementType":"geometry","stylers":[{"color":"#EAECEF"}]},
          {"featureType":"transit.station","elementType":"labels.text.fill","stylers":[{"color":"#52525B"}]},
          {"featureType":"water","elementType":"geometry","stylers":[{"color":"#DDE6EF"}]},
          {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#64748B"}]}
        ]
    """.trimIndent()
}
