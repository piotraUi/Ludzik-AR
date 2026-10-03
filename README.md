# Ludzik AR ✏️

Natywna aplikacja na Androida (Kotlin + Jetpack Compose + ARCore). Kamera pokazuje Twój pokój,
a po podłodze biegają ludziki narysowane ołówkiem w zeszycie: z niedbałymi kreskami, chwiejną animacją
klatka po klatce i białą obwódką, jakby ktoś je wyciął z kartki.

| Postać | Charakter |
|---|---|
| **Ludzik** | Klasyczny ludzik z patyków. Spokojnie spaceruje, macha do innych postaci i ucieka przed Gumką. |
| **Kleks** | Plama atramentu. Pełza, zostawia ślady na podłodze i chlapie przy każdym lądowaniu. Panicznie boi się Gumki. |
| **Gumka** | Dwukolorowa gumka do mazania. Poluje na inne postacie i je „wymazuje” (znikają na ~4,5 s). |
| **Ołówek** | Rysuje grafitowe linie po podłodze i rampy na wyższe płaszczyzny. Po tych mostach chodzą inne postacie. |
| **Pająk z długopisu** | Wspina się po ścianach i krawędziach mebli i zjeżdża na nitce. Długopisu nie da się wymazać, więc Gumka jest wobec niego bezradna. |

## Wymagania

- Android Studio Ladybug lub nowsze **albo** JDK 17+ i Android SDK (platforma 35, build-tools 35)
- Telefon z Androidem 8.0+ (minSdk 26) i obsługą ARCore ([lista urządzeń](https://developers.google.com/ar/devices))
- Emulator nie wystarczy do pełnego testu, bo potrzebna jest prawdziwa kamera i ARCore.

## Budowanie

```bash
# ścieżka do SDK: zmienna ANDROID_HOME albo plik local.properties (sdk.dir=...)
./gradlew assembleDebug
```

Gotowy plik APK znajdziesz w `app/build/outputs/apk/debug/app-debug.apk`.

Testy (JVM, bez telefonu):

```bash
./gradlew testDebugUnitTest
```

- `WorldSimulationTest` uruchamia 3 minuty symulacji 15 postaci na sztucznej podłodze ze stołem i ścianą.
  Sprawdza, że postacie wskakują na stół, Pająk się wspina, Gumka kogoś wymazuje, a Ołówek rysuje most.
  Sprawdza też, że postacie nie wypadają poza pokój i że klatka symulacji zajmuje mniej niż 2 ms.
- `DoodleRenderTest` generuje atlasy sprite'ów i zapisuje je jako PNG w `app/build/doodles/`.
- `ScreensRenderTest` robi zrzuty ekranów Compose do `app/build/screens/` (Robolectric, natywna grafika).

## Uruchomienie na telefonie

1. Włącz w telefonie **Opcje programisty → Debugowanie USB** i podłącz go kablem.
2. Wpisz `./gradlew installDebug` albo `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
   Możesz też uruchomić projekt w Android Studio przyciskiem ▶.
3. Otwórz aplikację, stuknij **Start**, zgódź się na kamerę. Jeśli brakuje *Usług Google Play dla AR*,
   aplikacja sama poprosi o ich instalację.
4. Powoli poruszaj telefonem i celuj w podłogę. Wykryte płaszczyzny pojawią się jako niebieska kratka.
5. Wybierz postać na dolnym pasku i dotknij kratki, żeby ją postawić (najwyżej 15 postaci).
   Dotknij postaci, a podskoczy i coś powie.
6. **Zrób zdjęcie** zapisuje kadr w `Obrazy/LudzikAR`. **Nagraj** zapisuje film (do 30 s, bez dźwięku)
   w `Filmy/LudzikAR`.

Jeśli podłoga długo nie jest rozpoznawana (np. jednolite płytki), po kilku sekundach możesz po prostu
dotknąć ekranu w miejscu podłogi. Postać stanie „na oko” (ARCore Instant Placement) i dopasuje się,
gdy telefon lepiej zrozumie scenę.

Najlepiej działa w jasnym pokoju, na podłodze ze wzorem (dywan, panele), ze stołem lub kanapą w kadrze.

## Architektura

```
com.ludzik.ar
├── MainActivity            nawigacja, uprawnienia, sprawdzanie i instalacja ARCore
├── ar/                     wszystko, co zależy od ARCore i OpenGL
│   ├── ArController        sesja ARCore, cykl życia, GLSurfaceView, zdjęcia/wideo, stan UI (StateFlow)
│   ├── ArRenderer          pętla klatki: kamera → płaszczyzny → naklejki → linie → postacie → dymki
│   ├── PlaneSurfaces       płaszczyzny ARCore → WalkSurface (stabilne obiekty dla logiki)
│   └── gl/                 shadery GLES 2.0: tło kamery, kratka, sprite'y-billboardy, linie-wstążki
├── characters/             logika i grafika postaci, bez zależności od ARCore (testowalna na JVM)
│   ├── Character           interfejs postaci + CharacterKind (opis, rysowanie, fabryka)
│   ├── CharacterRegistry   lista postaci w menu
│   ├── BaseCharacter       maszyna stanów: IDLE/WALK/RUN/JUMP/REACT/PATH/ERASED, skoki, mosty, ucieczka
│   ├── World               postacie, plamy, linie, dymki, zapytania o powierzchnie
│   ├── WalkSurface         wypukły wielokąt z marginesem krawędzi, ściany
│   ├── Ludzik, Kleks, Gumka, Olowek, Pajak
│   └── doodle/             DoodlePen (kreski z jitterem), atlas sprite'ów, plamy, dymki komiksowe
├── audio/                  SoundSynth (synteza efektów do WAV), SoundManager (SoundPool), Haptics
├── capture/                VideoRecorder (MediaRecorder + powierzchnia EGL), MediaSaver (MediaStore)
└── ui/                     Compose: okładka zeszytu, ekrany statusu, nakładka AR, wybór postaci
```

### Najważniejsze decyzje

- **Renderowanie:** zamiast Sceneform lub SceneView (Filament) jest lekki renderer GLES 2.0 bezpośrednio na
  ARCore. Postacie to płaskie billboardy 2D, więc pełny silnik 3D nie jest potrzebny, a własny renderer daje
  pełną kontrolę nad przezroczystością, liniami i nagrywaniem wideo (ta sama scena rysowana drugi raz do
  powierzchni enkodera).
- **Grafika bez assetów:** każda klatka jest rysowana kodem (`Canvas`/`Path`) przy starcie do atlasu tekstur
  (7 animacji × 4 klatki). Każda klatka ma inne ziarno losowości, dlatego kreski „gotują się” jak w animacji
  poklatkowej. Jedyny dołączony plik to czcionka Caveat (SIL OFL, licencja w `FONT-LICENSE-Caveat.txt`),
  bo systemowa czcionka „cursive” nie ma polskich znaków.
- **Wydajność:** `UpdateMode.LATEST_CAMERA_IMAGE`, więc animacje idą w tempie ekranu
  (kamera zostaje w domyślnym trybie, który lepiej wykrywa płaszczyzny). Symulacja 15 postaci zajmuje ~0,07 ms na klatkę (test na JVM).
  Na każdą postać przypada jedno wywołanie rysowania.
- **Depth API** (gdy telefon je obsługuje): postacie chowają się za realnymi przedmiotami, bo shader
  porównuje ich odległość z mapą głębi. Dotknięcie zmierzonego punktu podłogi stawia postać nawet tam,
  gdzie nie ma jeszcze kratki. Kolejne zabezpieczenie to ARCore Instant Placement, czyli stawianie „na oko”.
- **Dźwięki** są syntetyzowane przy pierwszym uruchomieniu (plum, skrzypienie, piszczenie gumki,
  tupot pająka, migawka…), zapisywane jako WAV w cache i odtwarzane przez SoundPool.

## Jak dodać nową postać

Wystarczy jedna klasa i jedna linijka w rejestrze. Przykład, `Spinacz`:

```kotlin
package com.ludzik.ar.characters

import com.ludzik.ar.characters.doodle.DoodlePen
import com.ludzik.ar.characters.doodle.phase
import kotlin.math.sin

class Spinacz(id: Int, start: Vec3, surface: WalkSurface) : BaseCharacter(id, Kind, start, surface) {

    override val walkSpeed = 0.1f

    // (opcjonalnie) własne decyzje; domyślnie: postój, spacer, bieg, skok na stół, akcja
    override fun decide() {
        if (rnd.nextFloat() < 0.3f) hop(0.2f) else defaultDecide()
    }

    // (opcjonalnie) reakcje na otoczenie, wołane co klatkę
    override fun sense(dt: Float) {
        val gumka = world.nearest(this, 0.5f) { it is Gumka } ?: return
        if (state != State.RUN && sameLevel(gumka)) flee(gumka.position)
    }

    companion object Kind : CharacterKind(
        id = "spinacz",
        displayName = "Spinacz",
        spriteSizeMeters = 0.16f,
        phrases = listOf("Trzymam się!", "Spinam, co się da"),
    ) {
        override fun create(id: Int, position: Vec3, surface: WalkSurface) = Spinacz(id, position, surface)

        // Rysowanie w kwadracie 0..1 (Y w dół, ziemia na y≈0.95), osobno dla każdej animacji i klatki.
        override fun draw(pen: DoodlePen, anim: Anim, frame: Int) {
            val bob = if (anim == Anim.WALK) sin(phase(frame)) * 0.02f else 0f
            pen.ellipse(0.5f, 0.6f + bob, 0.12f, 0.3f, color = 0xFF90A4AE.toInt())
            pen.ellipse(0.5f, 0.66f + bob, 0.07f, 0.2f, color = 0xFF90A4AE.toInt())
            pen.dot(0.47f, 0.45f + bob, 0.012f)
            pen.dot(0.53f, 0.45f + bob, 0.012f)
        }
    }
}
```

Potem dopisz postać w `CharacterRegistry.kinds`:

```kotlin
val kinds = listOf(Ludzik, Kleks, Gumka, Olowek, Pajak, Spinacz)
```

Ikona w menu, atlas tekstur, dymki, cień, wymazywanie i dotyk działają automatycznie.
`BaseCharacter` udostępnia gotowe akcje:
`idle`, `walkTo`, `wander`, `flee`, `hop`, `jumpTo`, `followPath`, `goToSurface`, `tryVisitOtherSurface`, `react`.
Nową postać warto obejrzeć w `app/build/doodles/` po uruchomieniu `DoodleRenderTest`.

## Co działa, a co jest do dopracowania

Działa (zweryfikowane buildem, testami JVM i podglądem grafiki): wszystkie 5 postaci z animacjami,
maszyna stanów, interakcje (pogoń, ucieczka, mosty, wymazywanie, wspinaczka, nitka), dymki,
dźwięki, wibracje, ekrany startowy/uprawnień/braku ARCore, zapis zdjęć i filmów.

Do dopracowania:
- Zasłanianie przez meble działa tylko na telefonach z Depth API (np. realme 14 Pro 5G). Na pozostałych postacie są zawsze na wierzchu. Krawędzie zasłaniania bywają postrzępione, bo mapa głębi ma niską rozdzielczość.
- Wideo nie ma dźwięku (wymagałoby uprawnienia do mikrofonu i miksowania efektów).
- Pająk wspina się tylko po ścianach wykrytych przez ARCore. Gładkie, jednolite ściany są wykrywane słabo.
- Dymki mają stały rozmiar w świecie, skalowany odległością. Z bardzo bliska mogą być duże.
- Renderer i nagrywanie nie zostały przetestowane na fizycznym urządzeniu w trakcie tworzenia projektu.
