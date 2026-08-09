# Network24 Android IPTV Player

> AI Context & Developer Guide  
> Version: 1.0  
> Project Type: Native Android (Kotlin) IPTV Client

---

# Purpose

This document exists primarily for AI assistants (ChatGPT, Claude, Gemini, Cursor, Copilot, Codex, etc.) and new developers.

Before making **any modification**, read this file completely.

The goal is to help understand the project architecture so that new features can be implemented without breaking existing functionality.

---

# Project Overview

Network24 is a modern IPTV client for Android devices.

Main features include:

- Live TV
- Movies
- TV Series
- Xtream Codes Login
- Dashboard
- Search
- Favorites
- Downloads
- Firebase Chat
- Push Notifications
- Local Cache
- EPG
- Custom Player
- Settings
- Auto Updater

The application follows a feature-based architecture instead of putting everything inside one package.

---

# Technology Stack

Language

- Kotlin

UI

- Android XML

Architecture

- Feature Based
- MVVM (where applicable)

Networking

- Retrofit
- OkHttp
- Gson

Image Loading

- Glide

Player

- ExoPlayer / Media3

Backend

- Xtream Codes API
- Custom APIs
- Firebase

Notifications

- Firebase Cloud Messaging

Storage

- SharedPreferences
- Local Cache
- JSON Cache

---

# High Level Architecture

```
User

↓

Splash

↓

Login

↓

Authentication

↓

Dashboard

↓

Live / Movies / Series

↓

Player

↓

History / Favorites / Cache
```

---

# Project Structure

```
app/

src/main/

java/com/network24/player/

common/

core/

features/

res/
```

---

# Package Responsibilities

---

## common/

Contains reusable UI and models.

Typical content

- adapters
- dialogs
- widgets
- formatters
- shared models

Anything reusable across multiple features should go here.

---

## core/

Core infrastructure.

Contains application-wide utilities.

Expected responsibilities

- API client
- Network layer
- Cache
- Base classes
- Constants
- Extensions
- Preferences
- Utility classes

Nothing feature-specific should exist here.

---

## features/

Contains every application module.

Each feature should remain isolated.

Current modules include:

Splash

Login

Dashboard

Live TV

Movies

Series

Player

Search

Favorites

Downloads

Notifications

Chat

Updater

Settings

Future modules should also be added here.

---

# Core Layer

Core should contain reusable infrastructure.

Examples

```
ApiClient

ApiService

CacheManager

MemoryCache

PreferenceManager

BaseActivity

Extensions

Constants
```

Never duplicate these classes.

Always reuse existing infrastructure.

---

# Feature Modules

Each feature should own:

- Activities
- Fragments
- ViewModels
- Repository
- Adapter
- Models

Do not mix Live TV logic inside Movies.

Keep modules isolated.

---

# Data Flow

```
Login

↓

API Authentication

↓

User Information

↓

Categories

↓

Streams

↓

Player

↓

History

↓

Cache
```

---

# Login Flow

User enters:

Server URL

Username

Password

↓

Validate

↓

Authenticate

↓

Store session

↓

Open Dashboard

Never modify authentication flow unless required.

Maintain backward compatibility.

---

# Dashboard

Dashboard is the navigation hub.

Possible sections

- Live TV

- Movies

- Series

- Search

- Favorites

- Downloads

- Chat

- Settings

Avoid putting business logic here.

Dashboard should only route users.

---

# Live TV Module

Responsibilities

- Categories

- Channels

- Search

- Favorites

- EPG

- Playback

Do not mix Movie logic here.

---

# Movies Module

Responsibilities

- Categories

- Movie details

- Search

- Playback

- Resume

---

# Series Module

Responsibilities

- Categories

- Seasons

- Episodes

- Episode Playback

---

# Player Module

Player is one of the most critical modules.

Responsibilities

- Stream playback

- Subtitle support

- Audio track selection

- Resume

- Continue Watching

- EPG

- Buffering

- Playback controls

Avoid unnecessary player rewrites.

Any player modification must remain backward compatible.

---

# Search

Should search across:

- Live

- Movies

- Series

Should remain fast.

Avoid unnecessary API requests.

---

# Favorites

Responsibilities

Store user favorites.

Never duplicate favorite storage.

Reuse existing implementation.

---

# Downloads

Responsible for offline downloads.

Future download-related features should remain inside this module.

---

# Notifications

Responsible for

- Push notifications

- Local notifications

- Announcement notifications

---

# Firebase Chat

Chat module is isolated.

Responsibilities

- Rooms

- Messages

- Notifications

Do not mix Firebase code elsewhere.

---

# Cache Layer

Cache exists to reduce unnecessary API calls.

Possible cache types

Memory

Disk

JSON

Preferences

Always reuse cache.

Do not bypass cache unless required.

---

# EPG

EPG should preferably be cached locally.

Ideal flow

```
Download

↓

Store locally

↓

Read locally

↓

Refresh periodically
```

Avoid requesting EPG for every playback if cached data exists.

---

# API Layer

All APIs should live inside:

```
core/api
```

Never create API interfaces inside feature packages unless necessary.

Reuse Retrofit instance.

---

# Preferences

Store only lightweight information.

Examples

- Login

- Theme

- User settings

- Tokens

Avoid storing large JSON.

---

# Constants

All hardcoded values belong inside

```
core/constants
```

Never hardcode URLs throughout the project.

---

# Utilities

Reusable helper methods belong inside

```
core/utils
```

Avoid duplicate helper methods.

---

# Adapters

Reusable adapters belong inside

```
common/adapters
```

Feature-specific adapters stay inside their own feature package.

---

# Images

Always use existing image loading implementation.

Do not introduce another image library.

---

# Logging

Avoid excessive logging in production.

Remove debug logs before release.

---

# Error Handling

Always

- Show user-friendly messages

- Log useful information

- Avoid crashes

---

# Coding Standards

Follow existing package structure.

Keep naming consistent.

Prefer extension functions.

Avoid giant Activities.

Avoid duplicate repositories.

Reuse utilities.

Keep methods small.

---

# Performance Guidelines

Avoid

Nested API calls

Blocking UI thread

Repeated JSON parsing

Large object creation

Unnecessary allocations

Prefer

Lazy loading

Caching

Pagination

Background processing

---

# Naming Convention

Packages

lowercase

Classes

PascalCase

Functions

camelCase

Variables

camelCase

Constants

UPPER_CASE

---

# New Feature Guidelines

When adding a feature

1. Create new feature package

2. Create repository

3. Create ViewModel

4. Create adapters

5. Reuse ApiClient

6. Reuse Cache

7. Register navigation

8. Avoid modifying unrelated modules

---

# Before Editing Existing Code

Understand

- Repository

- API

- Cache

- Models

- Adapters

before making changes.

---

# Things AI Should NEVER Do

Never rename packages.

Never remove existing APIs.

Never remove cache.

Never duplicate networking code.

Never duplicate repositories.

Never hardcode URLs.

Never break login.

Never change package names.

Never rewrite player unless necessary.

Never change database structure without migration.

---

# Preferred Extension Points

Networking

```
core/api
```

Cache

```
core/cache
```

Preferences

```
core/preferences
```

Utilities

```
core/utils
```

Feature logic

```
features/*
```

Reusable UI

```
common/*
```

---

# Future Features (Recommended)

- Continue Watching
- Watch History
- Recently Added
- Picture in Picture
- Multi View
- Recording
- Offline DRM
- Chromecast
- DLNA
- Watch Together
- Voice Search
- Analytics Dashboard
- Crash Reporting
- Theme Engine
- Playlist Import
- Multi Account Support

---

# AI Instructions

If an AI assistant is modifying this project:

1. Read existing implementation first.

2. Reuse existing architecture.

3. Avoid creating duplicate classes.

4. Follow feature-based architecture.

5. Preserve backward compatibility.

6. Keep networking centralized.

7. Keep cache centralized.

8. Reuse adapters whenever possible.

9. Keep code modular.

10. Keep UI responsive.

11. Never break login flow.

12. Never break player compatibility.

13. Do not introduce unnecessary dependencies.

14. Write clean, maintainable Kotlin.

15. Follow existing coding style.

---

# Build Notes

Recommended

Latest Android Studio Stable

Gradle version compatible with project

JDK 17

Keep dependency versions consistent.

---

# Maintainer Notes

This project is under active development.

Architecture should remain:

Clean

Modular

Scalable

Reusable

Backward Compatible

Any new contribution should respect the existing project structure and avoid unnecessary rewrites.