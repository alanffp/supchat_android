package com.example.supchat.utils

import android.util.Log
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.supchat.R

object ProfileImageManager {
    private const val TAG = "ProfileImageManager"

    // URL de base de votre serveur - AJUSTEZ SELON VOTRE CONFIGURATION
    private const val BASE_URL = "https://votre-serveur.com" // ← REMPLACEZ par votre URL

    /**
     * Charge une image de profil dans un ImageView
     */
    fun loadProfileImage(imageView: ImageView, profilePictureUrl: String?) {
        try {
            val context = imageView.context

            // Configuration Glide pour les images de profil
            val requestOptions = RequestOptions()
                .centerCrop()
                .placeholder(R.drawable.default_avatar) // Image par défaut pendant le chargement
                .error(R.drawable.default_avatar) // Image par défaut en cas d'erreur
                .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache sur disque
                .skipMemoryCache(false) // Utiliser le cache mémoire

            if (profilePictureUrl.isNullOrBlank()) {
                // Aucune image de profil, utiliser l'avatar par défaut
                Log.d(TAG, "Aucune image de profil, utilisation de l'avatar par défaut")
                Glide.with(context)
                    .load(R.drawable.default_avatar)
                    .apply(requestOptions)
                    .into(imageView)
            } else {
                // Construire l'URL complète si nécessaire
                val fullUrl = if (profilePictureUrl.startsWith("http")) {
                    profilePictureUrl
                } else {
                    "$BASE_URL/$profilePictureUrl"
                }

                Log.d(TAG, "Chargement de l'image de profil: $fullUrl")

                // Charger l'image avec cache-busting pour forcer le rechargement
                val urlWithTimestamp = "$fullUrl?t=${System.currentTimeMillis()}"

                Glide.with(context)
                    .load(urlWithTimestamp)
                    .apply(requestOptions)
                    .into(imageView)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du chargement de l'image de profil", e)

            // En cas d'erreur, charger l'image par défaut
            try {
                Glide.with(imageView.context)
                    .load(R.drawable.default_avatar)
                    .into(imageView)
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Erreur lors du chargement de l'image par défaut", fallbackError)
            }
        }
    }

    /**
     * Charge une image depuis une URL complète (utilisé après upload)
     */
    fun loadFromUrl(imageView: ImageView, imageUrl: String) {
        try {
            val context = imageView.context

            Log.d(TAG, "Chargement direct depuis URL: $imageUrl")

            val requestOptions = RequestOptions()
                .centerCrop()
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .diskCacheStrategy(DiskCacheStrategy.NONE) // Pas de cache pour les nouvelles images
                .skipMemoryCache(true) // Pas de cache mémoire pour les nouvelles images

            // Ajouter un timestamp pour forcer le rechargement
            val urlWithTimestamp = "$imageUrl?t=${System.currentTimeMillis()}"

            Glide.with(context)
                .load(urlWithTimestamp)
                .apply(requestOptions)
                .into(imageView)

        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du chargement depuis URL", e)
            loadProfileImage(imageView, null) // Fallback vers l'image par défaut
        }
    }

    /**
     * Précharge une image de profil en cache
     */
    fun preloadProfileImage(context: android.content.Context, profilePictureUrl: String?) {
        if (!profilePictureUrl.isNullOrBlank()) {
            try {
                val fullUrl = if (profilePictureUrl.startsWith("http")) {
                    profilePictureUrl
                } else {
                    "$BASE_URL/$profilePictureUrl"
                }

                Glide.with(context)
                    .load(fullUrl)
                    .preload()

                Log.d(TAG, "Préchargement de l'image: $fullUrl")

            } catch (e: Exception) {
                Log.w(TAG, "Erreur lors du préchargement", e)
            }
        }
    }

    /**
     * Vide le cache des images de profil
     */
    fun clearImageCache(context: android.content.Context) {
        try {
            // Vider le cache mémoire (sur le thread principal)
            Glide.get(context).clearMemory()

            // Vider le cache disque (sur un thread en arrière-plan)
            Thread {
                try {
                    Glide.get(context).clearDiskCache()
                    Log.d(TAG, "Cache d'images vidé")
                } catch (e: Exception) {
                    Log.w(TAG, "Erreur lors du vidage du cache disque", e)
                }
            }.start()

        } catch (e: Exception) {
            Log.w(TAG, "Erreur lors du vidage du cache", e)
        }
    }

    /**
     * Configuration des URLs - À appeler au démarrage de l'application
     */
    fun configureBaseUrl(baseUrl: String) {
        // Note: Dans une vraie application, vous pourriez stocker cela dans SharedPreferences
        // ou utiliser une configuration BuildConfig
        Log.d(TAG, "URL de base configurée: $baseUrl")
    }
}