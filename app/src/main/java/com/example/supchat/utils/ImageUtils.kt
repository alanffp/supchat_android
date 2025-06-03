package com.example.supchat.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ImageUtils {
    private const val TAG = "ImageUtils"
    private const val MAX_IMAGE_SIZE = 1024 // Taille max en pixels
    private const val JPEG_QUALITY = 85 // Qualité JPEG (0-100)
    private const val MAX_FILE_SIZE = 5 * 1024 * 1024 // 5MB en bytes

    /**
     * Prépare une image pour l'upload (redimensionnement, compression, rotation)
     */
    fun prepareImageForUpload(context: Context, imageUri: Uri): File? {
        try {
            // 1. Lire l'image depuis l'URI
            val inputStream = context.contentResolver.openInputStream(imageUri)
            if (inputStream == null) {
                Log.e(TAG, "Impossible d'ouvrir l'inputStream pour l'URI: $imageUri")
                return null
            }

            // 2. Décoder l'image en bitmap
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (originalBitmap == null) {
                Log.e(TAG, "Impossible de décoder l'image")
                return null
            }

            // 3. Corriger la rotation de l'image si nécessaire
            val rotatedBitmap = correctImageRotation(context, imageUri, originalBitmap)

            // 4. Redimensionner l'image si nécessaire
            val resizedBitmap = resizeImageIfNeeded(rotatedBitmap)

            // 5. Créer un fichier temporaire
            val outputFile = File(context.cacheDir, "compressed_image_${System.currentTimeMillis()}.jpg")

            // 6. Comprimer et sauvegarder
            val fileOutputStream = FileOutputStream(outputFile)
            val success = resizedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fileOutputStream)
            fileOutputStream.close()

            // Nettoyer les bitmaps
            if (rotatedBitmap != originalBitmap) {
                originalBitmap.recycle()
            }
            if (resizedBitmap != rotatedBitmap) {
                rotatedBitmap.recycle()
            }
            resizedBitmap.recycle()

            if (!success) {
                Log.e(TAG, "Échec de la compression de l'image")
                outputFile.delete()
                return null
            }

            Log.d(TAG, "Image préparée avec succès: ${outputFile.name}, taille: ${outputFile.length()} bytes")
            return outputFile

        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la préparation de l'image", e)
            return null
        }
    }

    /**
     * Corrige la rotation de l'image basée sur les données EXIF
     */
    private fun correctImageRotation(context: Context, imageUri: Uri, bitmap: Bitmap): Bitmap {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            if (inputStream != null) {
                val exif = ExifInterface(inputStream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                inputStream.close()

                val rotation = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }

                if (rotation != 0f) {
                    val matrix = Matrix()
                    matrix.postRotate(rotation)
                    return Bitmap.createBitmap(
                        bitmap, 0, 0,
                        bitmap.width, bitmap.height,
                        matrix, true
                    )
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Impossible de lire les données EXIF", e)
        }

        return bitmap
    }

    /**
     * Redimensionne l'image si elle dépasse la taille maximale
     */
    private fun resizeImageIfNeeded(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Si l'image est déjà assez petite, la retourner telle quelle
        if (width <= MAX_IMAGE_SIZE && height <= MAX_IMAGE_SIZE) {
            return bitmap
        }

        // Calculer le ratio de redimensionnement
        val ratio = minOf(
            MAX_IMAGE_SIZE.toFloat() / width,
            MAX_IMAGE_SIZE.toFloat() / height
        )

        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        Log.d(TAG, "Redimensionnement de ${width}x${height} vers ${newWidth}x${newHeight}")

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Vérifie si le fichier est une image valide
     */
    fun isValidImageFile(file: File): Boolean {
        if (!file.exists() || !file.isFile) {
            Log.e(TAG, "Le fichier n'existe pas ou n'est pas un fichier valide")
            return false
        }

        if (file.length() > MAX_FILE_SIZE) {
            Log.e(TAG, "Le fichier est trop volumineux: ${file.length()} bytes (max: $MAX_FILE_SIZE)")
            return false
        }

        // Vérifier l'extension
        val name = file.name.lowercase()
        val validExtensions = listOf(".jpg", ".jpeg", ".png", ".webp")
        if (!validExtensions.any { name.endsWith(it) }) {
            Log.e(TAG, "Extension de fichier non supportée: $name")
            return false
        }

        // Essayer de décoder l'image pour vérifier qu'elle est valide
        try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.absolutePath, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                Log.e(TAG, "Impossible de décoder l'image ou dimensions invalides")
                return false
            }

            Log.d(TAG, "Image valide: ${options.outWidth}x${options.outHeight}, ${file.length()} bytes")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la validation de l'image", e)
            return false
        }
    }

    /**
     * Nettoie les fichiers temporaires d'images
     */
    fun cleanupTempFiles(context: Context) {
        try {
            val cacheDir = context.cacheDir
            val files = cacheDir.listFiles { file ->
                file.name.startsWith("compressed_image_") && file.name.endsWith(".jpg")
            }

            files?.forEach { file ->
                if (file.lastModified() < System.currentTimeMillis() - 24 * 60 * 60 * 1000) { // 24h
                    file.delete()
                    Log.d(TAG, "Fichier temporaire supprimé: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur lors du nettoyage des fichiers temporaires", e)
        }
    }

    /**
     * Obtient les dimensions d'une image sans la charger entièrement en mémoire
     */
    fun getImageDimensions(context: Context, imageUri: Uri): Pair<Int, Int>? {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            if (inputStream != null) {
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close()

                return if (options.outWidth > 0 && options.outHeight > 0) {
                    Pair(options.outWidth, options.outHeight)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la lecture des dimensions", e)
        }
        return null
    }
}