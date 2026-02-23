# Jetpack Compose — Guida Completa per UI Dichiarativa Android (2026)

> Jetpack Compose è il toolkit moderno di Google per costruire UI native su Android. È l'equivalente diretto di SwiftUI per iOS. Questo documento copre tutto ciò che serve per costruire una app completa.

---

## 1. CONCETTI FONDAMENTALI

Compose funziona con **funzioni @Composable** — funzioni che descrivono UI. Non si usano XML, Fragment, RecyclerView o View. Tutto è Kotlin.

### Principi chiave
- **Dichiarativo**: descrivi *cosa* vuoi vedere, non *come* costruirlo
- **Recomposition**: quando lo stato cambia, le funzioni vengono ri-eseguite automaticamente
- **Unidirezionale**: i dati scorrono dall'alto verso il basso (state hoisting)
- **Composizione > Ereditarietà**: si compongono piccoli componenti insieme

---

## 2. SETUP PROGETTO (build.gradle.kts)

```kotlin
// build.gradle.kts (livello progetto)
plugins {
    id("com.android.application") version "8.8.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.10" apply false
    id("com.google.devtools.ksp") version "2.1.10-1.0.29" apply false
    id("com.google.dagger.hilt.android") version "2.54" apply false
}
```

```kotlin
// build.gradle.kts (livello app/modulo)
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.miaapp"
    compileSdk = 36  // Android 16

    defaultConfig {
        applicationId = "com.example.miaapp"
        minSdk = 26    // Android 8.0 (supporto minimo ragionevole nel 2026)
        targetSdk = 36 // Android 16
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Compose BOM — gestisce tutte le versioni Compose in un unico posto
    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
    implementation(composeBom)

    // Compose UI core
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")

    // Material 3 (include M3 Expressive)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
    implementation("androidx.compose.material:material-icons-extended")

    // Activity Compose
    implementation("androidx.activity:activity-compose:1.10.0")

    // Navigation 3 (NUOVA — 2025/2026)
    implementation("androidx.navigation3:navigation3-runtime:1.0.0")
    implementation("androidx.navigation3:navigation3-ui:1.0.0")

    // ViewModel + Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")

    // Hilt (dependency injection)
    implementation("com.google.dagger:hilt-android:2.54")
    ksp("com.google.dagger:hilt-android-compiler:2.54")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room (database locale)
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")

    // Immagini
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")

    // DataStore (preferenze)
    implementation("androidx.datastore:datastore-preferences:1.1.2")

    // Debug tools
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

---

## 3. ENTRY POINT — ACTIVITY

In Android moderno (2026), si usa una **singola Activity** per tutta l'app. Niente Fragment.

```kotlin
// MainActivity.kt
@AndroidEntryPoint  // Per Hilt dependency injection
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge (contenuto sotto status bar e navigation bar)
        enableEdgeToEdge()

        setContent {
            // Il tema Material 3 wrappa tutta la app
            MiaAppTheme {
                MiaApp()
            }
        }
    }
}
```

---

## 4. COMPOSABLE FUNCTIONS — LE BASI

### Hello World
```kotlin
@Composable
fun Saluto(nome: String) {
    Text(text = "Ciao, $nome!")
}

// Preview (equivalente di #Preview in SwiftUI)
@Preview(showBackground = true)
@Composable
fun SalutoPreview() {
    MiaAppTheme {
        Saluto("Mondo")
    }
}
```

### Layout di base
```kotlin
// Column = VStack in SwiftUI
@Composable
fun ColonnaEsempio() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Primo")
        Text("Secondo")
        Text("Terzo")
    }
}

// Row = HStack in SwiftUI
@Composable
fun RigaEsempio() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Sinistra")
        Text("Destra")
    }
}

// Box = ZStack in SwiftUI (sovrappone elementi)
@Composable
fun SovrapposizioneEsempio() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Image(/* sfondo */)
        Text("Testo sopra")
    }
}
```

---

## 5. MODIFIER — IL SISTEMA DI STYLING

I Modifier sono la catena di stile/layout. **L'ORDINE CONTA** (come i modifier in SwiftUI).

```kotlin
@Composable
fun ModifierEsempio() {
    Box(
        modifier = Modifier
            // Layout
            .fillMaxWidth()              // Occupa tutta la larghezza
            .height(200.dp)              // Altezza fissa
            .padding(16.dp)              // Padding ESTERNO (prima del background)

            // Decorazione
            .clip(RoundedCornerShape(16.dp))  // Arrotonda angoli
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp)
            )

            // Padding INTERNO (dopo il background)
            .padding(12.dp)

            // Interattività
            .clickable { /* azione click */ }

            // Scroll
            .verticalScroll(rememberScrollState())

            // Accessibilità
            .semantics { contentDescription = "Box principale" }
    ) {
        Text("Contenuto")
    }
}

// Modifier condizionale
fun Modifier.condizionale(
    condizione: Boolean,
    modificatore: Modifier.() -> Modifier
): Modifier = if (condizione) then(modificatore(Modifier)) else this

// Uso
Modifier
    .fillMaxWidth()
    .condizionale(isSelezionato) {
        background(Color.Blue.copy(alpha = 0.1f))
    }

// Modifier comuni che userai SEMPRE:
Modifier.fillMaxSize()         // Occupa tutto lo spazio
Modifier.fillMaxWidth()        // Occupa tutta la larghezza
Modifier.fillMaxHeight()       // Occupa tutta l'altezza
Modifier.width(100.dp)         // Larghezza fissa
Modifier.height(100.dp)        // Altezza fissa
Modifier.size(100.dp)          // Larghezza e altezza uguali
Modifier.padding(16.dp)        // Padding uguale
Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
Modifier.weight(1f)            // Peso relativo (solo in Row/Column)
Modifier.offset(x = 10.dp, y = 5.dp)
Modifier.alpha(0.5f)           // Opacità
Modifier.rotate(45f)           // Rotazione
Modifier.scale(1.5f)           // Scala
Modifier.shadow(8.dp, RoundedCornerShape(16.dp))
Modifier.aspectRatio(16f / 9f) // Rapporto d'aspetto
Modifier.wrapContentSize()     // Adatta al contenuto
```

---

## 6. STATE MANAGEMENT (FONDAMENTALE!)

### State in Compose
```kotlin
// remember — mantiene lo stato tra le recomposition (come @State in SwiftUI)
@Composable
fun Contatore() {
    var contatore by remember { mutableStateOf(0) }
    // "by" è la delegated property che fa auto-unwrap di .value

    Column {
        Text("Contatore: $contatore")
        Button(onClick = { contatore++ }) {
            Text("Incrementa")
        }
    }
}

// rememberSaveable — sopravvive anche alla rotazione dello schermo
@Composable
fun ContatorePersistente() {
    var contatore by rememberSaveable { mutableStateOf(0) }
    // Come remember ma salva in SavedInstanceState
}

// Tipi di State
var testo by remember { mutableStateOf("") }                  // Valore singolo
val lista = remember { mutableStateListOf<String>() }          // Lista osservabile
val mappa = remember { mutableStateMapOf<String, Int>() }      // Mappa osservabile

// derivedStateOf — stato calcolato (come computed property reattiva)
val listaFiltrata by remember(lista, filtro) {
    derivedStateOf { lista.filter { it.contains(filtro) } }
}

// snapshotFlow — converti State in Flow
val flusso = snapshotFlow { testo }
    .debounce(300)
    .distinctUntilChanged()
```

### State Hoisting Pattern (PATTERN PIÙ IMPORTANTE)
```kotlin
// SBAGLIATO — lo stato è dentro il componente, non è testabile né riusabile
@Composable
fun CampoTestoInterno() {
    var testo by remember { mutableStateOf("") }
    TextField(value = testo, onValueChange = { testo = it })
}

// CORRETTO — State Hoisting: lo stato è "alzato" al chiamante
@Composable
fun CampoTestoEsterno(
    valore: String,           // Stato passa GIÙ
    onCambiamento: (String) -> Unit,  // Eventi passano SU
    modifier: Modifier = Modifier
) {
    TextField(
        value = valore,
        onValueChange = onCambiamento,
        modifier = modifier
    )
}

// Uso dal parent
@Composable
fun Schermata() {
    var nome by remember { mutableStateOf("") }
    CampoTestoEsterno(
        valore = nome,
        onCambiamento = { nome = it }
    )
}
```

### ViewModel + State (PATTERN STANDARD PER PRODUZIONE)
```kotlin
// UiState
data class HomeUiState(
    val utenti: List<Utente> = emptyList(),
    val isLoading: Boolean = false,
    val errore: String? = null,
    val query: String = ""
)

// ViewModel
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: UtenteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        caricaUtenti()
    }

    fun caricaUtenti() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errore = null) }
            try {
                val utenti = repository.getUtenti()
                _uiState.update { it.copy(utenti = utenti, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    errore = e.message ?: "Errore sconosciuto",
                    isLoading = false
                )}
            }
        }
    }

    fun cercaUtenti(query: String) {
        _uiState.update { it.copy(query = query) }
        viewModelScope.launch {
            val risultati = repository.cerca(query)
            _uiState.update { it.copy(utenti = risultati) }
        }
    }

    fun eliminaUtente(id: Int) {
        viewModelScope.launch {
            repository.elimina(id)
            caricaUtenti()
        }
    }
}

// Schermata Compose
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigaDettaglio: (Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeContent(
        uiState = uiState,
        onCerca = viewModel::cercaUtenti,
        onRicarica = viewModel::caricaUtenti,
        onEliminaUtente = viewModel::eliminaUtente,
        onCliccaUtente = onNavigaDettaglio
    )
}

@Composable
fun HomeContent(
    uiState: HomeUiState,
    onCerca: (String) -> Unit,
    onRicarica: () -> Unit,
    onEliminaUtente: (Int) -> Unit,
    onCliccaUtente: (Int) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Utenti") })
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.errore != null -> {
                ErroreView(
                    messaggio = uiState.errore,
                    onRiprova = onRicarica,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.utenti,
                        key = { it.id }  // IMPORTANTE per performance
                    ) { utente ->
                        UtenteCard(
                            utente = utente,
                            onClick = { onCliccaUtente(utente.id) },
                            onElimina = { onEliminaUtente(utente.id) }
                        )
                    }
                }
            }
        }
    }
}
```

---

## 7. LISTE E GRIGLIE (EQUIVALENTI DI LIST/SCROLLVIEW IN SWIFTUI)

```kotlin
// LazyColumn = List in SwiftUI (lista verticale performante)
@Composable
fun ListaUtenti(utenti: List<Utente>, onClicca: (Utente) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        item {
            Text(
                "Tutti gli utenti",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        // Lista
        items(
            items = utenti,
            key = { it.id }  // Chiave unica per animazioni e performance
        ) { utente ->
            UtenteCard(utente = utente, onClick = { onClicca(utente) })
        }

        // Footer
        item {
            Text(
                "${utenti.size} utenti totali",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

// LazyRow = lista orizzontale
@Composable
fun CaroselloOrizzontale(items: List<Item>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.id }) { item ->
            ItemCard(item)
        }
    }
}

// LazyVerticalGrid = griglia (come LazyVGrid in SwiftUI)
@Composable
fun GrigliaFoto(foto: List<Foto>) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),  // Colonne adattive
        // oppure: columns = GridCells.Fixed(3),          // Colonne fisse
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(foto, key = { it.id }) { foto ->
            FotoCard(foto)
        }
    }
}

// LazyVerticalStaggeredGrid = griglia masonry (altezze variabili)
@Composable
fun MasonryGrid(items: List<Item>) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp
    ) {
        items(items, key = { it.id }) { item ->
            Card(modifier = Modifier.height(item.altezza.dp)) {
                // Contenuto
            }
        }
    }
}

// Sticky headers
@Composable
fun ListaConSezioni(sezioni: Map<String, List<Item>>) {
    LazyColumn {
        sezioni.forEach { (titolo, items) ->
            stickyHeader {
                Text(
                    titolo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(items, key = { it.id }) { item ->
                ItemRow(item)
            }
        }
    }
}

// Pull to refresh
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaConRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    val pullRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullRefreshState
    ) {
        content()
    }
}
```

---

## 8. COMPONENTI UI COMUNI

### Text e Typography
```kotlin
Text(
    text = "Titolo principale",
    style = MaterialTheme.typography.headlineLarge,
    color = MaterialTheme.colorScheme.onSurface,
    fontWeight = FontWeight.Bold,
    textAlign = TextAlign.Center,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.fillMaxWidth()
)

// Testo con stili misti (come AttributedString in Swift)
Text(
    buildAnnotatedString {
        append("Testo normale ")
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.Red)) {
            append("in grassetto rosso")
        }
        append(" e poi di nuovo normale")
    }
)

// Testo selezionabile
SelectionContainer {
    Text("Questo testo può essere selezionato e copiato")
}
```

### TextField (Input)
```kotlin
// TextField base Material 3
@Composable
fun CampoInput() {
    var testo by remember { mutableStateOf("") }

    OutlinedTextField(
        value = testo,
        onValueChange = { testo = it },
        label = { Text("Email") },
        placeholder = { Text("esempio@email.com") },
        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
        trailingIcon = {
            if (testo.isNotEmpty()) {
                IconButton(onClick = { testo = "" }) {
                    Icon(Icons.Default.Clear, contentDescription = "Cancella")
                }
            }
        },
        supportingText = {
            if (testo.isNotEmpty() && !testo.contains("@")) {
                Text("Email non valida")
            }
        },
        isError = testo.isNotEmpty() && !testo.contains("@"),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { /* azione al completamento */ }
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

// Password field
@Composable
fun CampoPassword() {
    var password by remember { mutableStateOf("") }
    var visibile by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        visualTransformation = if (visibile)
            VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visibile = !visibile }) {
                Icon(
                    if (visibile) Icons.Default.VisibilityOff
                    else Icons.Default.Visibility,
                    contentDescription = "Mostra/Nascondi password"
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
```

### Buttons
```kotlin
// Button primario (filled)
Button(onClick = { /* azione */ }) {
    Icon(Icons.Default.Add, contentDescription = null)
    Spacer(Modifier.width(8.dp))
    Text("Aggiungi")
}

// Button secondario (outlined)
OutlinedButton(onClick = { /* azione */ }) {
    Text("Annulla")
}

// Text button
TextButton(onClick = { /* azione */ }) {
    Text("Scopri di più")
}

// Filled tonal button
FilledTonalButton(onClick = { /* azione */ }) {
    Text("Opzione")
}

// Elevated button
ElevatedButton(onClick = { /* azione */ }) {
    Text("Continua")
}

// Icon button
IconButton(onClick = { /* azione */ }) {
    Icon(Icons.Default.Favorite, contentDescription = "Preferito")
}

// FAB (Floating Action Button)
FloatingActionButton(
    onClick = { /* azione */ },
    containerColor = MaterialTheme.colorScheme.primaryContainer
) {
    Icon(Icons.Default.Add, contentDescription = "Aggiungi")
}

// Extended FAB
ExtendedFloatingActionButton(
    onClick = { /* azione */ },
    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
    text = { Text("Componi") }
)
```

### Cards
```kotlin
@Composable
fun UtenteCard(
    utente: Utente,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar
            AsyncImage(
                model = utente.avatarUrl,
                contentDescription = "Avatar di ${utente.nome}",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = utente.nome,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = utente.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Azione
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

### Dialog e Bottom Sheet
```kotlin
// AlertDialog
@Composable
fun DialogConferma(
    titolo: String,
    messaggio: String,
    onConferma: () -> Unit,
    onAnnulla: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text(titolo) },
        text = { Text(messaggio) },
        confirmButton = {
            TextButton(onClick = onConferma) { Text("Conferma") }
        },
        dismissButton = {
            TextButton(onClick = onAnnulla) { Text("Annulla") }
        }
    )
}

// Modal Bottom Sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetEsempio() {
    var mostraSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    if (mostraSheet) {
        ModalBottomSheet(
            onDismissRequest = { mostraSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Opzioni", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                ListItem(
                    headlineContent = { Text("Condividi") },
                    leadingContent = { Icon(Icons.Default.Share, null) },
                    modifier = Modifier.clickable { /* ... */ }
                )
                ListItem(
                    headlineContent = { Text("Elimina") },
                    leadingContent = { Icon(Icons.Default.Delete, null) },
                    modifier = Modifier.clickable { /* ... */ }
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
```

### Immagini con Coil
```kotlin
// Caricamento immagine da URL
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data("https://example.com/foto.jpg")
        .crossfade(true)
        .build(),
    contentDescription = "Descrizione",
    modifier = Modifier
        .fillMaxWidth()
        .height(200.dp)
        .clip(RoundedCornerShape(12.dp)),
    contentScale = ContentScale.Crop,
    placeholder = painterResource(R.drawable.placeholder),
    error = painterResource(R.drawable.error_image)
)

// Immagine dalle risorse
Image(
    painter = painterResource(id = R.drawable.logo),
    contentDescription = "Logo app",
    modifier = Modifier.size(100.dp)
)

// Icona Material
Icon(
    imageVector = Icons.Filled.Favorite,
    contentDescription = "Preferito",
    tint = Color.Red
)
```

---

## 9. ANIMAZIONI

```kotlin
// Animazione di visibilità (come .transition in SwiftUI)
AnimatedVisibility(
    visible = isVisibile,
    enter = fadeIn() + slideInVertically(),
    exit = fadeOut() + slideOutVertically()
) {
    Text("Contenuto animato")
}

// Animazione di valore (come .animation in SwiftUI)
val colore by animateColorAsState(
    targetValue = if (isSelezionato) Color.Red else Color.Gray,
    animationSpec = tween(durationMillis = 300),
    label = "colore"
)

val dimensione by animateDpAsState(
    targetValue = if (espanso) 200.dp else 100.dp,
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    label = "dimensione"
)

val alpha by animateFloatAsState(
    targetValue = if (visibile) 1f else 0f,
    animationSpec = tween(500),
    label = "alpha"
)

// Animazione di contenuto (crossfade tra due composable)
AnimatedContent(
    targetState = schermataAttuale,
    transitionSpec = {
        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
    },
    label = "schermata"
) { schermata ->
    when (schermata) {
        Schermata.Home -> HomeContent()
        Schermata.Profilo -> ProfiloContent()
    }
}

// Crossfade semplice
Crossfade(targetState = paginaAttuale, label = "pagina") { pagina ->
    when (pagina) {
        0 -> PaginaUno()
        1 -> PaginaDue()
    }
}

// Infinite animation
val rotazione by rememberInfiniteTransition(label = "rotazione")
    .animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotazione"
    )

Icon(
    Icons.Default.Refresh,
    contentDescription = null,
    modifier = Modifier.rotate(rotazione)
)

// Spring animation (come Material 3 Expressive — animazioni bouncy)
val offsetX by animateDpAsState(
    targetValue = if (spostato) 200.dp else 0.dp,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    ),
    label = "offsetX"
)
```

---

## 10. SIDE EFFECTS

I side effects gestiscono operazioni non-UI dentro le composable function.

```kotlin
// LaunchedEffect — lancia una coroutine quando le chiavi cambiano
// (equivalente di .task in SwiftUI)
@Composable
fun SchermataDettaglio(utenteId: Int) {
    var utente by remember { mutableStateOf<Utente?>(null) }

    LaunchedEffect(utenteId) {
        // Eseguito quando utenteId cambia o al primo compose
        utente = repository.getUtente(utenteId)
    }

    utente?.let { UtenteView(it) }
}

// LaunchedEffect con Flow
@Composable
fun SchermataConEventi(viewModel: MioViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.eventi.collect { evento ->
            when (evento) {
                is Evento.MostraSnackbar -> {
                    snackbarHostState.showSnackbar(evento.messaggio)
                }
                is Evento.NavigaA -> { /* navigazione */ }
            }
        }
    }
}

// DisposableEffect — cleanup quando il composable esce dalla composizione
// (equivalente di .onDisappear in SwiftUI)
@Composable
fun SensorListener() {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService<SensorManager>()
        val listener = object : SensorEventListener { /* ... */ }
        sensorManager?.registerListener(listener, /* ... */)

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }
}

// SideEffect — eseguito ad ogni recomposition riuscita
@Composable
fun AnalyticsTracker(screenName: String) {
    SideEffect {
        analytics.logScreenView(screenName)
    }
}

// produceState — converte dati non-Compose in stato Compose
@Composable
fun UtenteState(id: Int): State<Utente?> = produceState<Utente?>(
    initialValue = null,
    key1 = id
) {
    value = repository.getUtente(id)
}

// rememberCoroutineScope — per lanciare coroutine da callback
@Composable
fun BottoneConCoroutine() {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Button(onClick = {
        scope.launch {
            snackbarHostState.showSnackbar("Azione eseguita!")
        }
    }) {
        Text("Clicca")
    }
}
```

---

## 11. SCAFFOLD E APP BAR

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold() {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("La Mia App") },
                navigationIcon = {
                    IconButton(onClick = { /* menu o back */ }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = { /* cerca */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Cerca")
                    }
                    IconButton(onClick = { /* altro */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Altro")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == "home",
                    onClick = { /* naviga */ },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentRoute == "cerca",
                    onClick = { /* naviga */ },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Cerca") }
                )
                NavigationBarItem(
                    selected = currentRoute == "profilo",
                    onClick = { /* naviga */ },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("Profilo") }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* azione */ }) {
                Icon(Icons.Default.Add, contentDescription = "Aggiungi")
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        // IMPORTANTE: passare sempre paddingValues al contenuto
        // Questo garantisce che il contenuto non sia coperto da top/bottom bar
        ContenutoApp(modifier = Modifier.padding(paddingValues))
    }
}
```

---

## 12. NAVIGATION 3 (NUOVA NAVIGAZIONE 2026)

Navigation 3 è la nuova libreria di navigazione costruita specificamente per Compose.
Lo sviluppatore controlla il back stack direttamente come una lista.

```kotlin
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.*

// 1. Definisci le chiavi di navigazione (type-safe)
@Serializable
data object HomeKey : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data class DetailKey(val itemId: String) : NavKey

@Serializable
data class ProfileKey(val userId: Int) : NavKey

// 2. Crea l'app con NavDisplay
@Composable
fun MiaApp() {
    // Il back stack è una semplice lista Compose che TU controlli
    val backStack = rememberNavBackStack(HomeKey)

    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider {

            entry<HomeKey> {
                HomeScreen(
                    onNavigaDettaglio = { itemId ->
                        backStack.add(DetailKey(itemId))
                    },
                    onNavigaSettings = {
                        backStack.add(SettingsKey)
                    }
                )
            }

            entry<DetailKey> { chiave ->
                DetailScreen(
                    itemId = chiave.itemId,
                    onIndietro = {
                        backStack.removeLastOrNull()
                    },
                    onNavigaProfilo = { userId ->
                        backStack.add(ProfileKey(userId))
                    }
                )
            }

            entry<SettingsKey> {
                SettingsScreen(
                    onIndietro = { backStack.removeLastOrNull() }
                )
            }

            entry<ProfileKey> { chiave ->
                ProfileScreen(
                    userId = chiave.userId,
                    onIndietro = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}

// Nota: per la navigation tradizionale (ancora molto diffusa), si usa:
// implementation("androidx.navigation:navigation-compose:2.9.7")
// con NavHost e NavController. Entrambi gli approcci sono validi nel 2026.
```

### Navigation tradizionale (alternativa ampiamente usata)
```kotlin
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {

        composable("home") {
            HomeScreen(
                onNavigaDettaglio = { id ->
                    navController.navigate("dettaglio/$id")
                }
            )
        }

        composable(
            route = "dettaglio/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: return@composable
            DettaglioScreen(
                id = id,
                onIndietro = { navController.popBackStack() }
            )
        }
    }
}
```

---

## 13. TEMI E DESIGN SYSTEM

```kotlin
// Theme.kt — Setup tema Material 3
@Composable
fun MiaAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,  // Dynamic Color (Material You)
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Dynamic color (colori basati sullo sfondo dell'utente)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = Color(0xFFBB86FC),
            secondary = Color(0xFF03DAC6),
            tertiary = Color(0xFF3700B3),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onPrimary = Color.White,
            onSecondary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White
        )
        else -> lightColorScheme(
            primary = Color(0xFF6200EE),
            secondary = Color(0xFF03DAC6),
            tertiary = Color(0xFF3700B3),
            background = Color(0xFFFFFBFE),
            surface = Color(0xFFFFFBFE),
            onPrimary = Color.White,
            onSecondary = Color.Black,
            onBackground = Color(0xFF1C1B1F),
            onSurface = Color(0xFF1C1B1F)
        )
    }

    val typography = Typography(
        headlineLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 40.sp
        ),
        titleLarge = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp
        ),
        bodyLarge = TextStyle(
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp
        ),
        labelSmall = TextStyle(
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        )
    )

    val shapes = Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = shapes,
        content = content
    )
}

// Uso dei token del tema nei composable
@Composable
fun EsempioStili() {
    Text(
        text = "Titolo",
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.primary
    )

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) { /* ... */ }
}
```

---

> **NOTA PER L'AI — MAPPATURA SWIFTUI → COMPOSE:**
> - `VStack` → `Column`
> - `HStack` → `Row`
> - `ZStack` → `Box`
> - `List` → `LazyColumn`
> - `ScrollView` → `Column(Modifier.verticalScroll(rememberScrollState()))`
> - `NavigationStack` → `NavDisplay` (Nav3) o `NavHost`
> - `.padding()` → `Modifier.padding()`
> - `.frame()` → `Modifier.size()` / `Modifier.width()` / `Modifier.height()`
> - `.background()` → `Modifier.background()`
> - `.cornerRadius()` → `Modifier.clip(RoundedCornerShape())`
> - `.overlay` → `Box { ... }` con contenuti sovrapposti
> - `@State` → `remember { mutableStateOf() }`
> - `@StateObject` → `viewModel()` o `hiltViewModel()`
> - `@ObservedObject` → parametro ViewModel
> - `@EnvironmentObject` → `CompositionLocalProvider`
> - `@Binding` → parametro `(value: T, onChange: (T) -> Unit)`
> - `.onAppear` → `LaunchedEffect(Unit)`
> - `.onDisappear` → `DisposableEffect { onDispose { } }`
> - `.task` → `LaunchedEffect(key)`
> - `.sheet()` → `ModalBottomSheet` o `AlertDialog`
> - `.navigationTitle()` → `TopAppBar(title = { ... })`
> - `TabView` → `NavigationBar` + `NavigationBarItem`
> - `Spacer()` → `Spacer(Modifier.height/width())`
> - `Divider()` → `HorizontalDivider()`
> - `AsyncImage` (SwiftUI) → `AsyncImage` (Coil)
> - `@ViewBuilder` → `@Composable () -> Unit` (content lambda)
> - `.toolbar` → azioni in `TopAppBar`
