package com.sentinel.quantum.security

/**
 * Offline E.164 calling-code directory used only for geographic presentation.
 * Entries with shared=true must never be presented as a verified single-country identity.
 */
object E164CallingCodeDirectory {
    data class Entry(
        val prefix: String,
        val isoCode: String,
        val name: String,
        val flag: String,
        val shared: Boolean = false
    )

    private fun e(prefix: String, iso: String, name: String, flag: String, shared: Boolean = false) =
        Entry(prefix, iso, name, flag, shared)

    private val entries = listOf(
        e("+93","AF","Afghanistan","🇦🇫"), e("+355","AL","Albanie","🇦🇱"), e("+213","DZ","Algérie","🇩🇿"),
        e("+1684","AS","Samoa américaines","🇦🇸"), e("+376","AD","Andorre","🇦🇩"), e("+244","AO","Angola","🇦🇴"),
        e("+1264","AI","Anguilla","🇦🇮"), e("+1268","AG","Antigua-et-Barbuda","🇦🇬"), e("+54","AR","Argentine","🇦🇷"),
        e("+374","AM","Arménie","🇦🇲"), e("+297","AW","Aruba","🇦🇼"), e("+247","AC","Ascension","🇦🇨"),
        e("+61","AU","Australie","🇦🇺"), e("+43","AT","Autriche","🇦🇹"), e("+994","AZ","Azerbaïdjan","🇦🇿"),
        e("+1242","BS","Bahamas","🇧🇸"), e("+973","BH","Bahreïn","🇧🇭"), e("+880","BD","Bangladesh","🇧🇩"),
        e("+1246","BB","Barbade","🇧🇧"), e("+375","BY","Biélorussie","🇧🇾"), e("+32","BE","Belgique","🇧🇪"),
        e("+501","BZ","Belize","🇧🇿"), e("+229","BJ","Bénin","🇧🇯"), e("+1441","BM","Bermudes","🇧🇲"),
        e("+975","BT","Bhoutan","🇧🇹"), e("+591","BO","Bolivie","🇧🇴"), e("+5997","BQ","Bonaire","🇧🇶"),
        e("+387","BA","Bosnie-Herzégovine","🇧🇦"), e("+267","BW","Botswana","🇧🇼"), e("+55","BR","Brésil","🇧🇷"),
        e("+246","IO","Diego Garcia","🇮🇴"), e("+1284","VG","Îles Vierges britanniques","🇻🇬"), e("+673","BN","Brunei","🇧🇳"),
        e("+359","BG","Bulgarie","🇧🇬"), e("+226","BF","Burkina Faso","🇧🇫"), e("+257","BI","Burundi","🇧🇮"),
        e("+855","KH","Cambodge","🇰🇭"), e("+237","CM","Cameroun","🇨🇲"), e("+1","NANP","Zone NANP","🌎",true),
        e("+238","CV","Cap-Vert","🇨🇻"), e("+1345","KY","Îles Caïmans","🇰🇾"), e("+236","CF","République centrafricaine","🇨🇫"),
        e("+235","TD","Tchad","🇹🇩"), e("+56","CL","Chili","🇨🇱"), e("+86","CN","Chine","🇨🇳"),
        e("+57","CO","Colombie","🇨🇴"), e("+269","KM","Comores","🇰🇲"), e("+242","CG","Congo","🇨🇬"),
        e("+243","CD","Rép. démocratique du Congo","🇨🇩"), e("+682","CK","Îles Cook","🇨🇰"), e("+506","CR","Costa Rica","🇨🇷"),
        e("+225","CI","Côte d’Ivoire","🇨🇮"), e("+385","HR","Croatie","🇭🇷"), e("+53","CU","Cuba","🇨🇺"),
        e("+5999","CW","Curaçao","🇨🇼"), e("+357","CY","Chypre","🇨🇾"), e("+420","CZ","Tchéquie","🇨🇿"),
        e("+45","DK","Danemark","🇩🇰"), e("+253","DJ","Djibouti","🇩🇯"), e("+1767","DM","Dominique","🇩🇲"),
        e("+1809","DO","République dominicaine","🇩🇴"), e("+1829","DO","République dominicaine","🇩🇴"), e("+1849","DO","République dominicaine","🇩🇴"),
        e("+593","EC","Équateur","🇪🇨"), e("+20","EG","Égypte","🇪🇬"), e("+503","SV","El Salvador","🇸🇻"),
        e("+240","GQ","Guinée équatoriale","🇬🇶"), e("+291","ER","Érythrée","🇪🇷"), e("+372","EE","Estonie","🇪🇪"),
        e("+268","SZ","Eswatini","🇸🇿"), e("+251","ET","Éthiopie","🇪🇹"), e("+500","FK","Îles Falkland","🇫🇰"),
        e("+298","FO","Îles Féroé","🇫🇴"), e("+679","FJ","Fidji","🇫🇯"), e("+358","FI","Finlande","🇫🇮"),
        e("+33","FR","France","🇫🇷"), e("+262","REYT","Départements et territoires français de l’océan Indien","🌐",true),
        e("+594","GF","Guyane française","🇬🇫"), e("+689","PF","Polynésie française","🇵🇫"), e("+241","GA","Gabon","🇬🇦"),
        e("+220","GM","Gambie","🇬🇲"), e("+995","GE","Géorgie","🇬🇪"), e("+49","DE","Allemagne","🇩🇪"),
        e("+233","GH","Ghana","🇬🇭"), e("+350","GI","Gibraltar","🇬🇮"), e("+30","GR","Grèce","🇬🇷"),
        e("+299","GL","Groenland","🇬🇱"), e("+1473","GD","Grenade","🇬🇩"), e("+590","GPBLMF","Guadeloupe / Saint-Barthélemy / Saint-Martin","🌐",true),
        e("+1671","GU","Guam","🇬🇺"), e("+502","GT","Guatemala","🇬🇹"), e("+224","GN","Guinée","🇬🇳"),
        e("+245","GW","Guinée-Bissau","🇬🇼"), e("+592","GY","Guyana","🇬🇾"), e("+509","HT","Haïti","🇭🇹"),
        e("+504","HN","Honduras","🇭🇳"), e("+852","HK","Hong Kong","🇭🇰"), e("+36","HU","Hongrie","🇭🇺"),
        e("+354","IS","Islande","🇮🇸"), e("+91","IN","Inde","🇮🇳"), e("+62","ID","Indonésie","🇮🇩"),
        e("+98","IR","Iran","🇮🇷"), e("+964","IQ","Irak","🇮🇶"), e("+353","IE","Irlande","🇮🇪"),
        e("+972","IL","Israël","🇮🇱"), e("+39","ITVA","Italie / Vatican","🌐",true), e("+1876","JM","Jamaïque","🇯🇲"),
        e("+81","JP","Japon","🇯🇵"), e("+962","JO","Jordanie","🇯🇴"), e("+7","RUKZ","Russie / Kazakhstan","🌐",true),
        e("+254","KE","Kenya","🇰🇪"), e("+686","KI","Kiribati","🇰🇮"), e("+850","KP","Corée du Nord","🇰🇵"),
        e("+82","KR","Corée du Sud","🇰🇷"), e("+965","KW","Koweït","🇰🇼"), e("+996","KG","Kirghizistan","🇰🇬"),
        e("+856","LA","Laos","🇱🇦"), e("+371","LV","Lettonie","🇱🇻"), e("+961","LB","Liban","🇱🇧"),
        e("+266","LS","Lesotho","🇱🇸"), e("+231","LR","Liberia","🇱🇷"), e("+218","LY","Libye","🇱🇾"),
        e("+423","LI","Liechtenstein","🇱🇮"), e("+370","LT","Lituanie","🇱🇹"), e("+352","LU","Luxembourg","🇱🇺"),
        e("+853","MO","Macao","🇲🇴"), e("+261","MG","Madagascar","🇲🇬"), e("+265","MW","Malawi","🇲🇼"),
        e("+60","MY","Malaisie","🇲🇾"), e("+960","MV","Maldives","🇲🇻"), e("+223","ML","Mali","🇲🇱"),
        e("+356","MT","Malte","🇲🇹"), e("+692","MH","Îles Marshall","🇲🇭"), e("+596","MQ","Martinique","🇲🇶"),
        e("+222","MR","Mauritanie","🇲🇷"), e("+230","MU","Maurice","🇲🇺"), e("+52","MX","Mexique","🇲🇽"),
        e("+691","FM","Micronésie","🇫🇲"), e("+373","MD","Moldavie","🇲🇩"), e("+377","MC","Monaco","🇲🇨"),
        e("+976","MN","Mongolie","🇲🇳"), e("+382","ME","Monténégro","🇲🇪"), e("+1664","MS","Montserrat","🇲🇸"),
        e("+212","MAEH","Maroc / Sahara occidental","🌐",true), e("+258","MZ","Mozambique","🇲🇿"), e("+95","MM","Myanmar","🇲🇲"),
        e("+264","NA","Namibie","🇳🇦"), e("+674","NR","Nauru","🇳🇷"), e("+977","NP","Népal","🇳🇵"),
        e("+31","NL","Pays-Bas","🇳🇱"), e("+687","NC","Nouvelle-Calédonie","🇳🇨"), e("+64","NZPN","Nouvelle-Zélande / Pitcairn","🌐",true),
        e("+505","NI","Nicaragua","🇳🇮"), e("+227","NE","Niger","🇳🇪"), e("+234","NG","Nigeria","🇳🇬"),
        e("+683","NU","Niue","🇳🇺"), e("+672","AQNF","Territoires extérieurs australiens","🌐",true), e("+389","MK","Macédoine du Nord","🇲🇰"),
        e("+47","NOSJ","Norvège / Svalbard et Jan Mayen","🌐",true), e("+968","OM","Oman","🇴🇲"), e("+92","PK","Pakistan","🇵🇰"),
        e("+680","PW","Palaos","🇵🇼"), e("+970","PS","Palestine","🇵🇸"), e("+507","PA","Panama","🇵🇦"),
        e("+675","PG","Papouasie-Nouvelle-Guinée","🇵🇬"), e("+595","PY","Paraguay","🇵🇾"), e("+51","PE","Pérou","🇵🇪"),
        e("+63","PH","Philippines","🇵🇭"), e("+48","PL","Pologne","🇵🇱"), e("+351","PT","Portugal","🇵🇹"),
        e("+1787","PR","Porto Rico","🇵🇷"), e("+1939","PR","Porto Rico","🇵🇷"), e("+974","QA","Qatar","🇶🇦"),
        e("+40","RO","Roumanie","🇷🇴"), e("+250","RW","Rwanda","🇷🇼"), e("+290","SH","Sainte-Hélène / Tristan da Cunha","🌐",true),
        e("+1869","KN","Saint-Christophe-et-Niévès","🇰🇳"), e("+1758","LC","Sainte-Lucie","🇱🇨"), e("+508","PM","Saint-Pierre-et-Miquelon","🇵🇲"),
        e("+1784","VC","Saint-Vincent-et-les-Grenadines","🇻🇨"), e("+685","WS","Samoa","🇼🇸"), e("+378","SM","Saint-Marin","🇸🇲"),
        e("+239","ST","Sao Tomé-et-Principe","🇸🇹"), e("+966","SA","Arabie saoudite","🇸🇦"), e("+221","SN","Sénégal","🇸🇳"),
        e("+381","RS","Serbie","🇷🇸"), e("+248","SC","Seychelles","🇸🇨"), e("+232","SL","Sierra Leone","🇸🇱"),
        e("+65","SG","Singapour","🇸🇬"), e("+5993","BQ","Saint-Eustache","🇧🇶"), e("+5994","BQ","Saba","🇧🇶"),
        e("+1721","SX","Saint-Martin (partie néerlandaise)","🇸🇽"), e("+421","SK","Slovaquie","🇸🇰"), e("+386","SI","Slovénie","🇸🇮"),
        e("+677","SB","Îles Salomon","🇸🇧"), e("+252","SO","Somalie","🇸🇴"), e("+27","ZA","Afrique du Sud","🇿🇦"),
        e("+211","SS","Soudan du Sud","🇸🇸"), e("+34","ES","Espagne","🇪🇸"), e("+94","LK","Sri Lanka","🇱🇰"),
        e("+249","SD","Soudan","🇸🇩"), e("+597","SR","Suriname","🇸🇷"), e("+46","SE","Suède","🇸🇪"),
        e("+41","CH","Suisse","🇨🇭"), e("+963","SY","Syrie","🇸🇾"), e("+886","TW","Taïwan","🇹🇼"),
        e("+992","TJ","Tadjikistan","🇹🇯"), e("+255","TZ","Tanzanie","🇹🇿"), e("+66","TH","Thaïlande","🇹🇭"),
        e("+670","TL","Timor-Leste","🇹🇱"), e("+228","TG","Togo","🇹🇬"), e("+690","TK","Tokelau","🇹🇰"),
        e("+676","TO","Tonga","🇹🇴"), e("+1868","TT","Trinité-et-Tobago","🇹🇹"), e("+216","TN","Tunisie","🇹🇳"),
        e("+90","TR","Türkiye","🇹🇷"), e("+993","TM","Turkménistan","🇹🇲"), e("+1649","TC","Îles Turques-et-Caïques","🇹🇨"),
        e("+688","TV","Tuvalu","🇹🇻"), e("+256","UG","Ouganda","🇺🇬"), e("+380","UA","Ukraine","🇺🇦"),
        e("+971","AE","Émirats arabes unis","🇦🇪"), e("+44","GB","Royaume-Uni","🇬🇧"), e("+25524","TZ-ZZ","Zanzibar (zone +255)","🇹🇿"),
        e("+598","UY","Uruguay","🇺🇾"), e("+998","UZ","Ouzbékistan","🇺🇿"), e("+678","VU","Vanuatu","🇻🇺"),
        e("+58","VE","Venezuela","🇻🇪"), e("+84","VN","Viêt Nam","🇻🇳"), e("+1340","VI","Îles Vierges des États-Unis","🇻🇮"),
        e("+681","WF","Wallis-et-Futuna","🇼🇫"), e("+967","YE","Yémen","🇾🇪"), e("+260","ZM","Zambie","🇿🇲"),
        e("+263","ZW","Zimbabwe","🇿🇼"),
        e("+800","GLOBAL","Service international gratuit","🌐",true), e("+808","GLOBAL","Service international à coût partagé","🌐",true),
        e("+870","GLOBAL","Inmarsat","🌐",true), e("+878","GLOBAL","Télécommunications personnelles universelles","🌐",true),
        e("+881","GLOBAL","Systèmes mobiles mondiaux par satellite","🌐",true), e("+882","GLOBAL","Réseaux internationaux","🌐",true),
        e("+883","GLOBAL","Réseaux internationaux / IoT-M2M","🌐",true), e("+888","GLOBAL","Télécommunications pour secours en cas de catastrophe","🌐",true),
        e("+979","GLOBAL","Service international surtaxé","🌐",true), e("+991","GLOBAL","Essais internationaux","🌐",true)
    ).sortedByDescending { it.prefix.length }

    fun resolve(number: String): Entry? =
        entries.firstOrNull { number.startsWith(it.prefix) }

    fun all(): List<Entry> = entries.toList()
}
