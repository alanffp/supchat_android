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
        val list = reactions?.map { Pair(it.key, it.value) } ?: emptyList()
        android.util.Log.d("Message", "getReactionsList pour message $id: $list")
        return list
    }

    /**
     * Vérifier si ce message a une réaction spécifique
     */
    fun hasReaction(emoji: String): Boolean {
        val has = reactions?.containsKey(emoji) == true
        android.util.Log.d("Message", "hasReaction $emoji pour message $id: $has")
        return has
    }
    /**
     * Récupérer le nombre total de réactions
     */
    fun getTotalReactions(): Int {
        val total = reactions?.values?.sum() ?: 0
        android.util.Log.d("Message", "getTotalReactions pour message $id: $total")
        return total
    }

    fun addReaction(emoji: String): Message {
        val newReactions = (reactions?.toMutableMap() ?: mutableMapOf()).apply {
            this[emoji] = (this[emoji] ?: 0) + 1
        }
        return this.copy(reactions = newReactions)
    }

    fun getReactionsDebugString(): String {
        return "Reactions pour message $id: $reactions"
    }

    fun hasReactions(): Boolean {
        return reactions != null && reactions.isNotEmpty()
    }

    fun getDebugSummary(): String {
        return "Message(id=$id, auteur=${getNomAuteur()}, hasReactions=${hasReactions()}, totalReactions=${getTotalReactions()})"
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