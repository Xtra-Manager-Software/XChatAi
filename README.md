# XChatAi - AI-Powered Chat Application

<div align="center">

![XChatAi Logo](https://img.shields.io/badge/XChatAi-v1.0.0-blue)
![Android](https://img.shields.io/badge/Platform-Android-green)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple)
![License](https://img.shields.io/badge/License-MIT-yellow)

**An intelligent chat application powered by LLM Groq.com with advanced features**

## 📱 Overview

XChatAi is a modern Android application that provides seamless interaction with advanced AI models through an intuitive and beautiful interface. Built with Jetpack Compose and following Material Design 3 guidelines, it offers a ChatGPT-like experience with unique features including real-time typewriter effects, thinking process visualization, and comprehensive conversation management.

### 🎯 Key Highlights

- 🤖 **Dual AI Models**: By LLM List by groq.com
- 💬 **ChatGPT-Style UI**: Character-by-character typewriter effect with blinking cursor
- 🧠 **Thinking Process**: Visualize AI's reasoning with expandable thinking sections
- 📝 **Rich Markdown**: Full support for code blocks, headings, lists, and more
- 🎨 **Material Design 3**: Dynamic colors, dark mode, and modern UI
- 🔒 **Secure Authentication**: Google Sign-In with Firebase
- 📊 **Rate Limiting**: Smart quota management with real-time tracking
- 💾 **Persistent Storage**: Offline conversation history with Room database

---

## ✨ Features

### 🎭 User Experience
- **Typewriter Effect**: Real-time character-by-character text streaming
- **Thinking Indicator**: Visual feedback showing AI processing state
- **Smooth Animations**: Fluid transitions and interactive elements
- **Auto-scroll**: Automatic scrolling during message streaming
- **Search Conversations**: Quick search through chat history
- **Profile Integration**: Google account with profile photo display

### 💬 Chat Management
- **Multiple Conversations**: Create and manage unlimited chat sessions
- **Conversation History**: Persistent storage of all conversations
- **Rename Conversations**: Customize conversation titles
- **Delete Conversations**: Remove unwanted chat history
- **Context Preservation**: Each conversation maintains its own context
- **Timestamps**: Track when messages were sent

### 🎨 Customization
- **Dark/Light Mode**: System-adaptive or manual theme switching
- **Dynamic Colors**: Material You color scheme (Android 12+)
- **AI Model Selection**: Switch between DeepSeek R1 and Llama models
- **Backup & Restore**: Export and import conversation data

### 📝 Content Features
- **Markdown Rendering**:
    - Code blocks with syntax highlighting
    - Headers (H1, H2, H3)
    - Bold text formatting
    - Bullet and numbered lists
    - Inline code snippets
- **Copy Functionality**: Copy code blocks and thinking processes
- **DeepSeek Thinking**: View AI's reasoning process in `<think>` tags
- **Expandable Sections**: Collapse/expand thinking process

### 🔐 Security & Limits
- **Rate Limiting**: 20 requests per 30 minutes per user
- **Real-time Tracking**: Live quota display in app bar
- **Warning System**: Alerts when approaching quota limit
- **Firebase Sync**: Server-side rate limit enforcement
- **Smart Polling**: Pauses updates while user is typing
- **Google Sign-In**: Secure authentication with Firebase

---

## 🛠️ Tech Stack

### Core Technologies
- **Language**: Kotlin 1.9+
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Minimum SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 14 (API 34)

### Libraries & Dependencies

#### UI & Design
- androidx.compose.material3:material3
- androidx.compose.ui:ui
- androidx.activity:activity-compose
- io.coil-kt:coil-compose (Image loading)


#### Architecture Components
- androidx.lifecycle:lifecycle-viewmodel-ktx
- androidx.lifecycle:lifecycle-runtime-compose
- androidx.navigation:navigation-compose

#### Database
- androidx.room:room-runtime
- androidx.room:room-ktx

#### Networking
- com.squareup.retrofit2:retrofit
- com.squareup.retrofit2:converter-gson
- com.squareup.okhttp3:logging-interceptor

#### Firebase
- com.google.firebase:firebase-auth
- com.google.firebase:firebase-database
- com.google.android.gms:play-services-auth


#### Utilities
- org.jetbrains.kotlinx:kotlinx-coroutines-android
- com.google.code.gson:gson


---

## 📚 Usage

### Basic Chat Flow

1. **Login**: Sign in with Google account
2. **Start Chat**: Tap "New Chat" or send first message
3. **Send Message**: Type message and press send button
4. **View Response**: Watch AI response appear character-by-character
5. **Explore Thinking**: Expand thinking section for DeepSeek R1 model

### Advanced Features

#### Copy Code Blocks
- Tap copy icon on code block header
- Code is copied to clipboard

#### Manage Conversations
- Long-press conversation to rename
- Tap delete icon to remove conversation
- Use search bar to find specific chats

#### Backup Data
1. Go to Settings
2. Tap "Backup Data"
3. Share exported JSON file

#### Restore Data
1. Go to Settings
2. Tap "Restore Data"
3. Select backup file

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to branch (`git push origin feature/AmazingFeature`)
5. Open Pull Request

### Code Style
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Add comments for complex logic
- Write unit tests for new features

---

## 🐛 Known Issues

- [ ] Markdown parsing may fail on extremely nested structures
- [ ] Rate limit counter may briefly desync during rapid requests
- [ ] Very long code blocks may cause performance issues on low-end devices

---

---

## 🙏 Acknowledgments

- [Groq](https://groq.com/) - For providing LLM Model List AI inference
- [Firebase](https://firebase.google.com/) - For authentication and database
- [Material Design](https://m3.material.io/) - For design guidelines
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - For modern UI toolkit

---

## 📧 Contact

**Developer**: Gustyx-Power 
**Email**: gustiadityamuzaky08@gmail.com  
**Telegram**: [@GustyxPower](t.me/GustyxPower)  
**Xtra Manager Software Community**: [Join Here](https://t.me/XtraManagerSoftware)

---

## 🌟 Support
If you find this project helpful, please give it a ⭐️!
Feel free to share feedback or report issues via GitHub Discussions or Issues.
---

<div align="center">

**Built with ❤️ using Kotlin and Jetpack Compose**

[![Made with Kotlin](https://img.shields.io/badge/Made%20with-Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpack-compose&logoColor=white)](https://developer.android.com/jetpack/compose)

</div>


