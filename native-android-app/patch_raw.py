p = "app/src/main/java/com/sentinel/quantum/ui/screens/OsintFeedScreen.kt"
with open(p, "r") as f: c = f.read()

# Ajout d-import Dispatchers si besoin, ou insertion directe dans le Composable
target = 'val context = LocalContext.current'
injection = '''
    var debugPayload by remember { mutableStateOf("Test brut...") }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val c = okhttp3.OkHttpClient()
                val req = okhttp3.Request.Builder().url("https://sentinelquantumvanguardaipro.pages.dev/public/phone-intelligence").build()
                c.newCall(req).execute().use { r ->
                    debugPayload = "HTTP ${r.code}: " + (r.body?.string()?.take(500) ?: "empty")
                }
            } catch (e: Exception) {
                debugPayload = "Net error: ${e.localizedMessage}"
            }
        }
    }
'''

if target in c and "debugPayload" not in c:
    c = c.replace(target, target + "\n" + injection)
    with open(p, "w") as f: f.write(c)
    print("Debug payload state injected.")
else:
    print("Target not found or already injected.")
