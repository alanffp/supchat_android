package com.example.supchat.models.response

import com.google.gson.annotations.SerializedName

/**
 * Modèle représentant un message dans la réponse API
 * Amélioré pour mieux gérer les réactions et les réponses
 */
data class Message(
    @SerializedName("_id") val id: String,
    val contenu: String,
    val auteur: Any, // Peut être String, Map ou Utilisateur
    val canal: String,
    val dateCreation: String? = null,
    val reactions: Map<String, Int>? = null,
    val reponses: List<Message>? = null,
    val estReponse: Boolean = false,
    val messageParent: String? = null
) {
    /**
     * Récupère le nom de l'auteur du message pour l'affichage
     */
    fun getNomAuteur(): String {
        return when (auteur) {
            is Map<*, *> -> {
                val map = auteur as Map<*, *>
                map["username"]?.toString()
                    ?: map["nom"]?.toString()
                    ?: map["email"]?.toString()
                    ?: "Utilisateur"
            }
            is String -> auteur.toString()
            else -> "Utilisateur"
        }
    }

    /**
     * Récupère l'ID de l'auteur
     */
    fun getAuteurId(): String {
        return when (auteur) {
            is Map<*, *> -> {
                val map = auteur as Map<*, *>
                map["_id"]?.toString() ?: map["id"]?.toString() ?: ""
            }
            is String -> auteur.toString()
            else -> ""
        }
    }

    /**
     * Obtenir les réactions sous forme de liste
     */
    fun getReactionsList(): List<Pair<String, Int>> {
        return reactions?.map { Pair(it.key, it.value) } ?: emptyList()
    }

    /**
     * Vérifier si ce message a une réaction spécifique
     */
    fun hasReaction(emoji: String): Boolean {
        return reactions?.containsKey(emoji) == true
    }

    /**
     * Récupérer le nombre total de réactions
     */
    fun getTotalReactions(): Int {
        return reactions?.values?.sum() ?: 0
    }

    /**
     * Vérifier si ce message est une réponse
     */
    fun isReply(): Boolean = estReponse || messageParent != null

    /**
     * Vérifier si ce message a des réponses
     */
    fun hasReplies(): Boolean = reponses != null && reponses.isNotEmpty()

    /**
     * Récupérer le nombre de réponses
     */
    fun getReplyCount(): Int = reponses?.size ?: 0
}