package br.edu.ifpe

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
