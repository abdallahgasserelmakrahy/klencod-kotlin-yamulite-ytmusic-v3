# YaMuLite Android — отчёт о проделанной работе

Дата: 2026-04-24
Устройство для отладки: POCO X8 Pro (HyperOS, Android 16, API 36, codename `klee`)

## 1. Подготовка окружения

| Компонент | Версия / путь |
|---|---|
| JDK | OpenJDK 17.0.19 (Homebrew formula `openjdk@17`) |
| Android Studio | 2025.3.4 «Panda» (`/Applications/Android Studio.app`) |
| Android SDK | `~/Library/Android/sdk` |
| platform-tools | adb 1.0.41 (37.0.0) |
| build-tools | 36.1.0 |
| platforms | `android-36` (Android 16) |
| cmdline-tools | latest (sdkmanager 20.0) |
| Лицензии | приняты все 7 |
| Gradle (для генерации wrapper) | 9.4.1 (Homebrew) |

В `~/.zshrc` дописаны `JAVA_HOME`, `ANDROID_HOME`, дополнения `PATH`.

Грабли при установке:
- Cask `temurin@17` требует sudo — заменён на formula `openjdk@17`.
- Apk install на HyperOS заблокирован `INSTALL_FAILED_USER_RESTRICTED` — лечится включением «Установка через USB» + «Проверка приложений по USB → выкл.» в режиме разработчика.

## 2. Стек

- **Kotlin 2.0.21** + **Jetpack Compose** (BOM 2024.12.01) + **Material 3**
- **Hilt 2.52** (DI) через KSP `2.0.21-1.0.28`
- **Navigation Compose 2.8.5**
- **Retrofit 2.11.0** + **OkHttp 4.12.0** + **kotlinx.serialization 1.7.3**
- **Coil 3.0.4** (изображения, дисковый кэш через OkHttp)
- **Media3 ExoPlayer 1.5.1** (воспроизведение)
- **DataStore Preferences 1.1.1** (токен, настройки)

`compileSdk = 36`, `targetSdk = 36`, `minSdk = 26` (Android 8+).

Понижение Kotlin с 2.1.0 до 2.0.21 — сделано из-за несовместимости Hilt 2.52 с метаданными Kotlin 2.1.

## 3. Структура проекта

```
yamulite-android/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/{values,xml}/...
│       └── java/dev/pdv/yamulite/
│           ├── YaMuLiteApp.kt          @HiltAndroidApp + Coil ImageLoader
│           ├── MainActivity.kt          Compose host
│           ├── data/
│           │   ├── auth/                OAuth Device Flow
│           │   ├── music/               Yandex.Music API + лайки + URL-резолвер
│           │   │   └── dto/             DTO (kotlinx.serialization)
│           │   ├── network/             FlexibleStringSerializer
│           │   ├── playback/            AudioPlayer (ExoPlayer) + DownloadManager
│           │   └── settings/            Quality enum + SettingsStore
│           ├── di/                      Hilt-модули (Network, Music)
│           └── ui/
│               ├── AppNavigation.kt     auth-gate
│               ├── AppViewModel.kt
│               ├── auth/                AuthScreen (device flow)
│               ├── main/
│               │   ├── MainScreen.kt    Bottom nav (4 таба)
│               │   ├── components/      CoverImage, TrackRow, ArtistRow/AlbumRow
│               │   ├── search/          SearchScreen + ViewModel
│               │   ├── favorites/       FavoritesScreen + ViewModel
│               │   ├── nowplaying/      NowPlayingScreen + ViewModel
│               │   └── settings/        SettingsScreen + ViewModel
│               └── theme/Theme.kt
├── gradle/
│   ├── libs.versions.toml               version catalog
│   └── wrapper/gradle-wrapper.{jar,properties}
├── build.gradle.kts                     root
├── settings.gradle.kts
├── gradle.properties
├── gradlew
└── local.properties                     sdk.dir
```

Всего 36 Kotlin-файлов, ~2200 строк кода.

## 4. Реализованные функции

### 4.1. Авторизация (OAuth Device Flow)

- Используется community client_id `23cabbbdc6cd418abb4b39c32c41195d` + соответствующий публичный client_secret.
- Endpoint'ы: `POST oauth.yandex.ru/device/code`, `POST oauth.yandex.ru/token` (polling).
- На экране отображается:
  - Заголовок «Войдите в Яндекс»
  - Кликабельная ссылка `verification_url` (открывает браузер)
  - Большой моноширинный код, **тап = копирование в буфер**, snackbar «Код скопирован»
  - Прогресс «Ожидаем подтверждения…»
- Polling уважает `interval` и `slow_down` от сервера.
- Токен сохраняется в зашифрованный DataStore. При следующем запуске экран авторизации не показывается — через `tokenFlow` UI автоматически переключается на главный.
- `device_id` (UUID) генерируется один раз и переиспользуется.

### 4.2. Music API client (api.music.yandex.net)

`MusicApi` (Retrofit):

| Метод | Назначение |
|---|---|
| `GET /account/status` | uid пользователя |
| `GET /search?text=&type=track\|artist\|album` | поиск |
| `GET /users/{uid}/likes/tracks` | список id лайков |
| `POST /tracks` (form `track-ids=`) | подгрузка деталей по id |
| `POST /users/{uid}/likes/tracks/add-multiple` | лайк |
| `POST /users/{uid}/likes/tracks/remove` | анлайк |
| `GET /tracks/{id}/download-info` | список вариантов скачивания |

`AuthInterceptor` добавляет `Authorization: OAuth <token>` ко всем запросам к music API.

`MusicRepository` кэширует `uid`, держит `StateFlow<Set<String>> likedIds` для мгновенной реакции UI на лайки.

### 4.3. UI: Bottom Navigation

4 вкладки: **Поиск**, **Избранное**, **Сейчас**, **Настройки** (`Material 3 NavigationBar`).

### 4.4. Поиск

- Поле ввода с дебаунсом 350 мс.
- Табы Треки / Исполнители / Альбомы переключают тип запроса.
- Списки рендерятся через `LazyColumn`; обложки — `Coil AsyncImage`, кэшируются на диске (≤ 64 MB).
- Формат строки трека: **«Артист — Название»** (по требованию).

### 4.5. Избранное

- При входе на вкладку загружается `likes/tracks` → детали через `tracks` → `LazyColumn`.
- Использует общий `TrackRow`.
- Сердечко и кнопка скачивания — те же, что в Поиске.

### 4.6. Сейчас (плеер)

- Большая обложка 280 dp.
- «Артист — Название» крупным шрифтом.
- Ряд: ⏮ Prev | ▶/⏸ | ⏭ Next | ❤️.
- Prev/Next автоматически отключаются на краях очереди.
- Сердечко лайкает/убирает текущий трек (как просил пользователь).
- При окончании трека автопереход на следующий.
- Очередь = список треков, из которого был сделан тап (Поиск или Избранное).

### 4.7. Воспроизведение (Media3 ExoPlayer)

- `StreamUrlResolver` запрашивает `download-info`, выбирает mp3 ≤ предпочитаемого битрейта, тянет XML с CDN, считает MD5-подпись (community salt `XGRlBW9FXlekgbPrRHuSiA`), собирает прямой URL вида `https://{host}/get-mp3/{md5}/{ts}{path}`.
- Если трек скачан локально — играется с диска, без сети.
- Для каждого трека резолв происходит лениво при play.

### 4.8. Настройки

- Радио-выбор качества: Low (64) / Normal (128) / High (192, по умолчанию) / Best (320 kbps).
- Хранится в DataStore.
- Применяется и для стриминга, и для скачивания.

### 4.9. Скачивание / Оффлайн

- `DownloadManager` пишет файлы в `context.filesDir/tracks/{trackId}.mp3` (внутреннее хранилище, без runtime permissions).
- При запуске сканирует папку → все найденные mp3 = `Done`.
- В строке трека — иконка облачка справа от сердечка:
  - ☁️ → не скачан, тап начинает загрузку
  - круговой прогресс с долей → скачивается
  - ☁️✓ (синее) → скачан, тап удаляет
  - ⚠️ (красное) → ошибка, тап повторяет
- Прогресс обновляется каждые 64 KB (через `MutableStateFlow<Map<String, DownloadInfo>>`).
- Скачивание идёт во временный `.mp3.part`, переименовывается в `.mp3` по успеху.

## 5. Известные ограничения

1. **Неофициальный API.** Endpoint'ы `api.music.yandex.net` и community client_id юридически в серой зоне; Яндекс может в любой момент изменить формат ответов.
2. **Без real-time обновлений лайков с сервера.** Состояние `likedIds` обновляется только при загрузке Избранного и локальных тапах.
3. **Opus не поддержан.** MP3, AAC и FLAC реализованы; Opus остаётся вне поддержки. Если выбранный кодек недоступен (например, нет FLAC-подписки) — стриминг автоматически переключается на MP3.
4. **Нет редактирования очереди.** Очередь — текущий список (Поиск/Избранное) на момент тапа. Менять порядок вручную нельзя. Очередь сохраняется на диск (`queue.json`) и восстанавливается после гибели процесса.
5. **Нет seek-бара / прогресса трека.** В плеере только play/pause/prev/next, без позиции.
6. **Ошибки лайков заглушаются** (`runCatching` без UI). Поведение «выглядит как сработало» при сетевом сбое.

## 6. Сборка и установка

```bash
cd /Users/pdv/dev/yamulite-android

# debug-сборка
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :app:assembleDebug

# установка на подключённый телефон
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.pdv.yamulite/.MainActivity
```

APK ~18 MB. Первая сборка ~1 мин (загрузка зависимостей), повторные ~5 сек.

## 7. Что было решено по ходу

- Многие DTO Yandex'а нестабильны: артисты без `id`, `id` трека приходит то строкой, то числом, `like/unlike` возвращает не строку а объект `{revision: N}`. Поправлено: дефолты для optional полей + `FlexibleStringSerializer` для `TrackDto.id` + типизация ответов лайков как `RevisionDto`.
- Логирование OkHttp в logcat изначально было невидно — на отладке смотрел через сообщения об ошибках в UI и через скриншоты с экрана.
- Изначально пытался положить проект в `/Users/pdv/dev/yamulite/` — оказалось, там уже существует Python+PyQt6 десктоп-клиент. Переехал в `/Users/pdv/dev/yamulite-android/`.

## 8. Возможные следующие шаги

- `MediaSessionService` + foreground notification → background playback и Bluetooth-кнопки.
- Seek-bar и отображение времени трека.
- Открытие альбома / артиста при тапе в результатах поиска.
- Отображение скачанных треков отдельной вкладкой (раздел «Скачанное»).
- `WorkManager` для устойчивых загрузок.
- Logout-кнопка в Настройках.
- Обработка истечения OAuth-токена и refresh.
