# Mappatura Completa: Swift/SwiftUI/iOS → Kotlin/Compose/Android

> Questa è una reference veloce e completa per convertire concetti, pattern e codice da un'app iOS (Swift + SwiftUI + Liquid Glass) a un'app Android nativa (Kotlin + Jetpack Compose + Material 3 Expressive). Pensata per un'AI che deve fare il porting.

---

## 1. LINGUAGGIO: SWIFT → KOTLIN

| Swift | Kotlin | Note |
|-------|--------|------|
| `let x = 5` | `val x = 5` | Immutabile |
| `var x = 5` | `var x = 5` | Mutabile |
| `String?` | `String?` | Nullable (identico!) |
| `if let x = optional { }` | `optional?.let { x -> }` oppure `if (optional != null) { }` | Unwrap opzionale |
| `guard let x = opt else { return }` | `val x = opt ?: return` | Early return |
| `optional ?? defaultValue` | `optional ?: defaultValue` | Valore di default |
| `optional!` | `optional!!` | Force unwrap (evitare) |
| `as? Type` | `as? Type` | Safe cast |
| `as! Type` | `as Type` | Force cast |
| `func nome(param: Tipo) -> Ritorno` | `fun nome(param: Tipo): Ritorno` | Funzione |
| `(Int) -> String` | `(Int) -> String` | Tipo closure/lambda |
| `{ param in ... }` | `{ param -> ... }` | Closure/Lambda |
| `$0` | `it` | Parametro implicito singolo |
| `switch x { case ... }` | `when (x) { ... }` | Pattern matching |
| `for item in lista` | `for (item in lista)` | For-in loop |
| `for i in 0..<10` | `for (i in 0 until 10)` | Range esclusivo |
| `for i in 0...10` | `for (i in 0..10)` | Range inclusivo |
| `[String]` | `List<String>` | Array |
| `[String: Int]` | `Map<String, Int>` | Dizionario |
| `Set<String>` | `Set<String>` | Set (identico!) |
| `struct` | `data class` | Value type con equality |
| `class` | `class` | Reference type |
| `enum` semplice | `enum class` | Enumerazione |
| `enum` con associated values | `sealed class` | Tipo somma |
| `protocol` | `interface` | Contratto |
| `extension Type` | `fun Type.metodo()` | Extension |
| `static` | `companion object` | Membri statici |
| `lazy var` | `by lazy { }` | Inizializzazione pigra |
| `@propertyWrapper` | `by Delegates.xxx` | Proprietà delegate |
| `Codable` | `@Serializable` | Serializzazione |
| `try/catch` | `try/catch` | Gestione errori |
| `Result<T, Error>` | `Result<T>` | Tipo risultato |
| `async/await` | `suspend fun` + `coroutineScope` | Concorrenza |
| `Task { }` | `viewModelScope.launch { }` | Lancio task asincrono |
| `AsyncSequence` | `Flow<T>` | Stream asincrono |
| `@Published` | `MutableStateFlow<T>` | Stato osservabile |
| `CurrentValueSubject` | `MutableStateFlow<T>` | Stato con valore corrente |
| `PassthroughSubject` | `MutableSharedFlow<T>` | Evento senza valore corrente |
| `DispatchQueue.main` | `Dispatchers.Main` | Thread UI |
| `DispatchQueue.global()` | `Dispatchers.IO` / `Dispatchers.Default` | Background thread |
| `#if DEBUG` | `if (BuildConfig.DEBUG)` | Compilazione condizionale |
| `print()` | `println()` / `Log.d()` | Log |
| `typealias` | `typealias` | Alias di tipo (identico!) |

---

## 2. UI FRAMEWORK: SWIFTUI → JETPACK COMPOSE

| SwiftUI | Jetpack Compose | Note |
|---------|-----------------|------|
| `struct MyView: View` | `@Composable fun MyView()` | Componente UI |
| `var body: some View` | Il corpo della funzione `@Composable` | Contenuto |
| `VStack` | `Column` | Stack verticale |
| `HStack` | `Row` | Stack orizzontale |
| `ZStack` | `Box` | Stack sovrapposto |
| `List` | `LazyColumn` | Lista scrollabile |
| `ScrollView` | `Column(Modifier.verticalScroll(...))` | Scroll generico |
| `LazyVGrid` | `LazyVerticalGrid` | Griglia |
| `LazyHGrid` | `LazyHorizontalGrid` | Griglia orizzontale |
| `ForEach` | `items()` dentro `LazyColumn` | Iterazione in lista |
| `NavigationStack` | `NavDisplay` (Nav3) o `NavHost` | Stack navigazione |
| `NavigationLink` | `backStack.add(Key)` o `navController.navigate()` | Navigazione |
| `.navigationTitle()` | `TopAppBar(title = { })` | Titolo navigazione |
| `.toolbar { }` | `actions = { }` in `TopAppBar` | Azioni toolbar |
| `TabView` | `NavigationBar` + `NavigationBarItem` | Tab bar |
| `@State` | `remember { mutableStateOf() }` | Stato locale |
| `@Binding` | `(value: T, onChange: (T) -> Unit)` | Binding bidirezionale |
| `@StateObject` | `viewModel()` / `hiltViewModel()` | ViewModel |
| `@ObservedObject` | Parametro ViewModel | Osserva ViewModel |
| `@EnvironmentObject` | `CompositionLocalProvider` | Iniezione globale |
| `@Environment(\.colorScheme)` | `isSystemInDarkTheme()` | Tema sistema |
| `.onAppear { }` | `LaunchedEffect(Unit) { }` | Al primo appear |
| `.onDisappear { }` | `DisposableEffect { onDispose { } }` | Al disappear |
| `.task { }` | `LaunchedEffect(key) { }` | Task async al compose |
| `.onChange(of: value)` | `LaunchedEffect(value) { }` | Reagire a cambiamenti |
| `.sheet(isPresented:)` | `ModalBottomSheet` | Sheet modale |
| `.alert(isPresented:)` | `AlertDialog` | Dialogo alert |
| `.confirmationDialog` | `AlertDialog` o `ModalBottomSheet` | Dialogo conferma |
| `Spacer()` | `Spacer(Modifier.height/width())` | Spazio vuoto |
| `Divider()` | `HorizontalDivider()` | Linea separatrice |
| `ProgressView()` | `CircularProgressIndicator()` | Indicatore caricamento |
| `Toggle` | `Switch` | Interruttore |
| `Slider` | `Slider` | Slider (identico!) |
| `Picker` | `DropdownMenu` + `ExposedDropdownMenuBox` | Selettore |
| `DatePicker` | `DatePicker` (M3) | Selezione data |
| `TextField` | `OutlinedTextField` o `TextField` | Campo testo |
| `SecureField` | `TextField` + `PasswordVisualTransformation` | Campo password |
| `Image(systemName:)` | `Icon(Icons.Default.Name, ...)` | Icona sistema |
| `AsyncImage` | `AsyncImage` (Coil) | Immagine da URL |
| `Color.primary` | `MaterialTheme.colorScheme.primary` | Colore tema |
| `#Preview` | `@Preview @Composable fun XPreview()` | Preview |
| `.padding()` | `Modifier.padding()` | Padding |
| `.frame(width:height:)` | `Modifier.size/width/height()` | Dimensioni |
| `.background()` | `Modifier.background()` | Sfondo |
| `.cornerRadius()` | `Modifier.clip(RoundedCornerShape())` | Angoli arrotondati |
| `.opacity()` | `Modifier.alpha()` | Opacità |
| `.rotationEffect()` | `Modifier.rotate()` | Rotazione |
| `.scaleEffect()` | `Modifier.scale()` | Scala |
| `.shadow()` | `Modifier.shadow()` | Ombra |
| `.overlay()` | `Box { ... }` sovrapposti | Overlay |
| `.clipShape()` | `Modifier.clip()` | Clip forma |
| `.edgesIgnoringSafeArea()` | `Modifier.fillMaxSize()` + gestione insets | Ignora safe area |
| `GeometryReader` | `BoxWithConstraints` o `Modifier.onSizeChanged` | Lettura dimensioni |
| `.withAnimation { }` | `animateXAsState()` | Animazione |
| `.transition(.slide)` | `AnimatedVisibility(enter/exit)` | Transizioni |
| `.matchedGeometryEffect` | `SharedTransitionLayout` | Shared element |
| `@ViewBuilder` | `@Composable () -> Unit` (content lambda) | Builder di contenuto |
| `AnyView` / `some View` | Non necessario — Compose è type-safe | Erasure |

---

## 3. DESIGN SYSTEM: LIQUID GLASS → MATERIAL 3 EXPRESSIVE

| iOS (Liquid Glass) | Android (M3 Expressive) | Note |
|--------------------|------------------------|------|
| Effetto vetro/blur | `Surface` con colori tonali | Android non usa blur per componenti |
| `.ultraThinMaterial` | `MaterialTheme.colorScheme.surfaceContainerLow` | Superficie sottile |
| `.thinMaterial` | `MaterialTheme.colorScheme.surfaceContainer` | Superficie media |
| `.regularMaterial` | `MaterialTheme.colorScheme.surfaceContainerHigh` | Superficie alta |
| Tint color | `MaterialTheme.colorScheme.primary` | Colore accento |
| System background | `MaterialTheme.colorScheme.background` | Sfondo |
| Tab bar iOS | `NavigationBar` M3 | Barra navigazione |
| Navigation bar iOS | `TopAppBar` M3 | Barra superiore |
| SF Symbols | `Icons.Default.*` (Material Icons) | Icone |
| Segmented control | Connected `ButtonGroup` (M3 Expressive) | Segmenti |
| Context menu | `DropdownMenu` | Menu contestuale |
| Action sheet | `ModalBottomSheet` | Sheet azioni |
| Pull to refresh | `PullToRefreshBox` | Refresh |
| Search bar iOS | `SearchBar` M3 | Barra ricerca |
| iOS spring animations | `spring(DampingRatioMediumBouncy)` | Animazioni elastiche |
| Haptic feedback | `LocalHapticFeedback.current.performHapticFeedback()` | Feedback aptico |
| Dynamic Island | Non esiste — usa notifiche Live Updates | — |
| App Intents | App Actions / Shortcuts | Azioni rapide |
| WidgetKit | Glance (Jetpack) | Widget home |

---

## 4. FRAMEWORK E LIBRERIE: IOS → ANDROID

| iOS | Android | Funzione |
|-----|---------|----------|
| UIKit / SwiftUI | Jetpack Compose | UI Framework |
| Xcode | Android Studio | IDE |
| SPM / CocoaPods | Gradle + libs.versions.toml | Gestione dipendenze |
| Core Data / SwiftData | Room | Database locale |
| UserDefaults | DataStore Preferences | Preferenze |
| Keychain | EncryptedSharedPreferences | Storage sicuro |
| URLSession | Retrofit + OkHttp | Networking |
| Codable | kotlinx.serialization | JSON parsing |
| Combine | Kotlin Flow / Coroutines | Reattività |
| XCTest | JUnit + MockK | Unit test |
| XCUITest | Compose Test + Espresso | UI test |
| SwiftUI Previews | @Preview + Compose Preview | Anteprime |
| Kingfisher / SDWebImage | Coil | Caricamento immagini |
| StoreKit | Google Play Billing | Acquisti in-app |
| CloudKit | Firebase Firestore | Database cloud |
| APNs | Firebase Cloud Messaging | Push notifications |
| HealthKit | Health Connect | Dati salute |
| Core Location | FusedLocationProviderClient | Geolocalizzazione |
| MapKit | Google Maps SDK | Mappe |
| AVFoundation | CameraX / Media3 | Fotocamera/Video |
| Core ML | TensorFlow Lite / ML Kit | Machine Learning |
| ARKit | ARCore | Realtà aumentata |
| Core Bluetooth | Android BLE API | Bluetooth |
| PassKit | Google Wallet API | Wallet/Pass |
| App Clips | Instant Apps | Versione leggera |
| TestFlight | Firebase App Distribution / Play Console Internal Testing | Beta testing |
| App Store Connect | Google Play Console | Pubblicazione |

---

## 5. ESEMPIO COMPLETO DI CONVERSIONE

### iOS (Swift + SwiftUI)
```swift
struct UserListView: View {
    @StateObject private var viewModel = UserListViewModel()

    var body: some View {
        NavigationStack {
            List(viewModel.users) { user in
                NavigationLink(value: user) {
                    HStack {
                        AsyncImage(url: URL(string: user.avatarUrl ?? ""))
                            .frame(width: 44, height: 44)
                            .clipShape(Circle())
                        VStack(alignment: .leading) {
                            Text(user.name)
                                .font(.headline)
                            Text(user.email)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("Utenti")
            .refreshable { await viewModel.loadUsers() }
            .overlay {
                if viewModel.isLoading {
                    ProgressView()
                }
            }
        }
    }
}
```

### Android (Kotlin + Jetpack Compose) — Conversione corretta
```kotlin
@Composable
fun UserListScreen(
    viewModel: UserListViewModel = hiltViewModel(),
    onNavigateToDetail: (Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Utenti") })
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.loadUsers() },
            modifier = Modifier.padding(paddingValues)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = uiState.users,
                    key = { it.id }
                ) { user ->
                    Card(
                        onClick = { onNavigateToDetail(user.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AsyncImage(
                                model = user.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Column {
                                Text(
                                    text = user.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = user.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
```

---

## 6. CHECKLIST PRE-PUBBLICAZIONE ANDROID

- [ ] Tema M3 con supporto dark mode e dynamic color
- [ ] Layout adattivo per telefono, tablet, foldable
- [ ] Edge-to-edge abilitato (`enableEdgeToEdge()`)
- [ ] Gestione corretta degli insets (status bar, navigation bar, keyboard)
- [ ] Back gesture (predictive back) supportato
- [ ] Permessi richiesti solo quando necessari (runtime permissions)
- [ ] ProGuard/R8 configurato per release build
- [ ] `minSdk` appropriato (26+ nel 2026)
- [ ] `targetSdk = 36` (Android 16)
- [ ] App firmata con chiave di release
- [ ] Icona adaptive (foreground + background layers)
- [ ] Splash screen con la nuova API SplashScreen
- [ ] Accessibilità: contentDescription su tutti gli elementi interattivi
- [ ] Localizzazione: stringhe in `strings.xml`, supporto RTL

---

> **NOTA FINALE PER L'AI**: Quando fai il porting da iOS ad Android:
> 1. NON tradurre pixel per pixel il design iOS. Adatta al linguaggio Android (Material 3).
> 2. Usa SEMPRE i componenti M3 standard — sono già belli e coerenti.
> 3. Le animazioni devono essere spring-based per coerenza con M3 Expressive.
> 4. La navigazione Android ha il back button (hardware/gesture) — gestiscilo.
> 5. Android ha la barra di stato in alto e la barra di navigazione in basso — usa gli insets.
> 6. Non dimenticare di gestire rotazione schermo e multi-window.
> 7. Usa `hiltViewModel()` per i ViewModel e `collectAsStateWithLifecycle()` per i Flow.
> 8. Ogni componente custom deve accettare `modifier: Modifier = Modifier` come parametro.
> 9. Testa su emulatore con diverse dimensioni schermo.
> 10. Le versioni nella sezione dipendenze potrebbero essere cambiate — verifica sempre le ultime stabili.
