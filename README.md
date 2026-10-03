# Ludzik 3D ✏️

Gra na Androida (Kotlin, Jetpack Compose, Filament). Chodzisz z widoku pierwszej osoby po realistycznym
mieszkaniu i respisz w nim ludziki narysowane ołówkiem w zeszycie oraz prawdziwe przedmioty z fizyką.

- **Realistyczna scena 3D:** parkiet w jodełkę, sofa, fotel, stoliki, regał, roślina i lampa.
  Modele i tekstury PBR pochodzą z Poly Haven (CC0). Oświetlenie to HDRI wnętrza plus słońce wpadające
  przez okno, z cieniami, SSAO, bloomem i tone mappingiem AgX.
- **Pierwsza osoba:** lewym kciukiem chodzisz (joystick pojawia się pod palcem), prawym się rozglądasz.
  Możesz skakać, wchodzić na meble i kopać piłkę. Na ekranie widać twoją narysowaną rękę z ołówkiem.
- **Respienie:** celujesz krzyżykiem i naciskasz „Respnij”.
  - Ludziki (Ludzik, Kleks, Gumka, Ołówek, Pająk) stają na podłodze albo na blacie.
  - Przedmioty (piłka, kaczka, karton, jabłko, skrzynka) spadają, odbijają się i toczą.
    „Rzuć” rzuca wybranym przedmiotem przed siebie.
- **Interakcje:** dotknij ludzika, a podskoczy i coś powie. Dotknij przedmiotu, a go popchniesz.
  Rzucona piłka trafiająca w ludzika powoduje „Ała!”.
  Ludziki zachowują się jak wcześniej:
  - Gumka goni i wymazuje,
  - Kleks ucieka i chlapie,
  - Ołówek rysuje mosty na meble,
  - Pająk wspina się po ścianach.
- **Zdjęcie:** zapisuje kadr do galerii (`Obrazy/LudzikAR`).

## Wymagania

- JDK 17+ i Android SDK (platforma 35) albo Android Studio
- Telefon z Androidem 8.0+ i OpenGL ES 3.0 (praktycznie każdy telefon z ostatnich lat).
  ARCore ani kamera nie są potrzebne.

## Budowanie i uruchomienie

```bash
./gradlew assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # instalacja na podłączonym telefonie (debugowanie USB)
./gradlew testDebugUnitTest      # testy na JVM, bez telefonu
```

Testy:

| Test | Co sprawdza |
|---|---|
| `GameLogicTest` | Kolizje gracza (sofa blokuje, na stolik da się wskoczyć), fizykę (odbicie i spoczynek piłki, karton na stoliku, rzucone rzeczy zostają w pokoju), kopanie piłki, celowanie i respienie na podłodze i na stole, 2 minuty życia 15 ludzików w pokoju |
| `WorldSimulationTest` | Zachowania ludzików na sztucznej scenie |
| `DoodleRenderTest` | Generuje atlasy postaci do `app/build/doodles/` |
| `ScreensRenderTest` | Zrzuty interfejsu do `app/build/screens/` |

## Zasoby 3D

Gotowe zasoby leżą w `app/src/main/assets` (ok. 23 MB). Generuje je skrypt:

```bash
pip install pillow
python3 tools/build_assets.py
```

Skrypt pobiera modele, tekstury i HDRI z [Poly Haven](https://polyhaven.com) (CC0), pakuje je do `.glb`
i buduje geometrię pokoju (ściany z oknem, listwy, dywan). Na koniec zapisuje układ mebli i bryły
kolizji do `RoomLayout.kt`, dzięki czemu fizykę i AI da się testować bez telefonu.
Żeby zmienić umeblowanie, edytuj listę `FURNITURE` w skrypcie i uruchom go ponownie.

## Architektura

```
com.ludzik.game
├── MainActivity        ekran startowy ↔ gra, dźwięk, wibracje, zdjęcia
├── game/
│   ├── GameController  cała logika bez Androida: gracz, fizyka, ludziki, celowanie, respienie
│   └── GameHost        SurfaceView + pętla Choreographera (logika → renderer → Filament)
├── render/
│   ├── FilamentCore    silnik, kamera, jakość obrazu (AgX, SSAO, bloom, cienie DPCF)
│   ├── GameRenderer    ładowanie sceny krok po kroku, IBL z HDRI, słońce, pule przedmiotów
│   └── BillboardBatch  rysunkowe ludziki, plamy, linie i dymki jako dynamiczne prostokąty
│                       (materiał unlit z gltfio, bez własnych shaderów do kompilowania)
├── scene/              RoomLayout (wygenerowany), RoomGeometry (kolizje, promienie),
│                       CharacterSpace (most między pokojem a światem ludzików)
├── physics/            PhysicsWorld: grawitacja, odbicia, toczenie, zderzenia, kopanie
├── player/             Player: chodzenie, skok, wchodzenie na meble
├── characters/         ludziki z zeszytu (bez zmian od wersji AR) + rysowanie ołówkiem
├── audio/              syntezowane dźwięki i wibracje
└── ui/                 Compose: okładka zeszytu, HUD, joystick, menu respienia
```

Ludziki mają w świecie gry ok. 72 cm, czyli są 3× większe niż w wersji AR. Ich logika działa w swojej
dawnej skali, a `CharacterSpace` przelicza pozycje (×3). Dzięki temu skoki na stół, mosty Ołówka
i wspinaczka Pająka działają bez zmian.

## Jak dodać nową postać

Jak wcześniej: jedna klasa z `companion object Kind : CharacterKind(...)` w pakiecie `characters`
i jedna linijka w `CharacterRegistry.kinds`. Postać sama pojawi się w menu respienia i w atlasie tekstur.

## Jak dodać nowy przedmiot

1. Dopisz model Poly Haven do `SPAWNABLES` w `tools/build_assets.py` i uruchom skrypt.
2. Dodaj rysunkową ikonę w `ObjectIcons.kt`.

## Co działa, a co jest do dopracowania

Zweryfikowane bez telefonu:
- build (`assembleDebug`) i testy JVM,
- wygląd sceny: podgląd tych samych modeli i świateł w przeglądarce (three.js),
- zrzuty interfejsu.

Nie dało się sprawdzić w tym środowisku (brak emulatora z GPU), wymaga testu na telefonie:
- pierwsze uruchomienie renderera Filament,
- dobór jasności: ekspozycja kamery, natężenie IBL i słońca (`GameRenderer.IBL_INTENSITY`, `SUN_LUX`),
- płynność na słabszych telefonach (rozdzielczość dynamiczna jest włączona).

Pomysły na dalszy rozwój:
- więcej pokoi,
- cienie rzucane przez ludziki,
- nagrywanie wideo,
- dźwięk przestrzenny.
