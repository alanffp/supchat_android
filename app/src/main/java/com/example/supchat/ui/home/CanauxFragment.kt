package com.example.supchat.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.supchat.R
import com.example.supchat.api.ApiClient
import com.example.supchat.models.response.Canal
import com.example.supchat.models.response.CanauxResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class CanauxFragment : Fragment() {
    private lateinit var canauxContainer: LinearLayout
    private lateinit var serverNameTextView: TextView
    private lateinit var workspaceName: String
    private var workspaceId: String? = null

    companion object {
        private const val TAG = "CanauxFragment" // Consistent tag for logging

        fun newInstance(workspaceName: String, workspaceId: String? = null): CanauxFragment {
            Log.d(TAG, "newInstance called with workspaceName=$workspaceName, workspaceId=$workspaceId")
            val fragment = CanauxFragment()
            val args = Bundle()
            args.putString("workspaceName", workspaceName)
            workspaceId?.let { args.putString("workspaceId", it) }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            workspaceName = it.getString("workspaceName", "Workspace")
            workspaceId = it.getString("workspaceId")
            Log.d(TAG, "onCreate: workspaceName=$workspaceName, workspaceId=$workspaceId")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_channels, container, false)
        canauxContainer = view.findViewById(R.id.channels_container)
        serverNameTextView = view.findViewById(R.id.server_name)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Afficher le nom du workspace
        serverNameTextView.text = workspaceName

        // Charger les canaux pour ce workspace
        chargerCanauxPourWorkspace()
    }

    private fun chargerCanauxPourWorkspace() {
        // Récupérer le token d'authentification
        val token = requireActivity().getSharedPreferences(
            "SupChatPrefs",
            android.content.Context.MODE_PRIVATE
        ).getString("auth_token", "")

        Log.d(TAG, "Token pour charger les canaux: ${if (token?.isNotEmpty() == true) "présent" else "absent"}")

        // S'assurer que nous avons un ID valide
        if (workspaceId.isNullOrEmpty()) {
            Log.e(TAG, "Impossible de charger les canaux: ID du workspace manquant ou vide")
            Toast.makeText(
                context,
                "Impossible de charger les canaux: ID du workspace manquant",
                Toast.LENGTH_SHORT
            ).show()
            afficherMessageCanauxVides()
            return
        }

        Log.d(TAG, "Chargement des canaux pour workspaceId=$workspaceId")

        if (token.isNullOrEmpty()) {
            Log.e(TAG, "Token d'authentification manquant")
            Toast.makeText(
                context,
                "Session expirée, veuillez vous reconnecter",
                Toast.LENGTH_SHORT
            ).show()
            (activity as? HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
            return
        }

        ApiClient.getCanauxForWorkspace(token, workspaceId!!)
            .enqueue(object : Callback<CanauxResponse> {
                override fun onResponse(
                    call: Call<CanauxResponse>,
                    response: Response<CanauxResponse>
                ) {
                    if (!isAdded) {
                        Log.w(TAG, "Fragment n'est plus attaché à l'activité")
                        return
                    }

                    // Log plus de détails sur la réponse pour le débogage
                    Log.d(TAG, "Code de réponse API: ${response.code()}")
                    val rawResponse = response.raw().toString()
                    Log.d(TAG, "Réponse brute: $rawResponse")

                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        Log.d(TAG, "Réponse API: $responseBody")

                        if (responseBody?.data?.canaux == null) {
                            Log.e(TAG, "Réponse API: data ou canaux est null")
                            afficherMessageCanauxVides()
                            return
                        }

                        val canaux = responseBody.data.canaux
                        if (canaux.isNotEmpty()) {
                            Log.d(TAG, "Canaux récupérés: ${canaux.size}")
                            Log.d(TAG, "Premier canal: ${canaux.firstOrNull()}")
                            afficherCanaux(canaux)
                        } else {
                            Log.d(TAG, "Liste de canaux vide")
                            afficherMessageCanauxVides()
                        }
                    } else if (response.code() == 401) {
                        // Gérer l'erreur d'authentification
                        Log.e(TAG, "Erreur 401: Authentification expirée ou invalide")
                        Toast.makeText(
                            context,
                            "Session expirée, veuillez vous reconnecter",
                            Toast.LENGTH_SHORT
                        ).show()
                        (activity as? HomeActivity)?.redirectToLogin("Session expirée, veuillez vous reconnecter")
                    } else if (response.code() == 403) {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "Erreur 403: Accès refusé - $errorBody")
                        Toast.makeText(
                            context,
                            "Vous n'avez pas accès à ce workspace",
                            Toast.LENGTH_SHORT
                        ).show()
                        afficherMessageCanauxVides("Vous n'avez pas l'autorisation d'accéder à ce workspace")
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "Erreur API: ${response.code()}, corps: $errorBody")
                        Toast.makeText(
                            context,
                            "Erreur de récupération des canaux: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                        afficherMessageCanauxVides()
                    }
                }

                override fun onFailure(call: Call<CanauxResponse>, t: Throwable) {
                    if (!isAdded) return

                    Log.e(TAG, "Échec de l'appel API", t)

                    val errorMessage = when (t) {
                        is IllegalStateException -> "Erreur dans le format des données: ${t.message}"
                        is java.net.UnknownHostException -> "Erreur de connexion: Vérifiez votre connexion Internet"
                        is java.net.SocketTimeoutException -> "Délai d'attente dépassé pour la connexion"
                        else -> "Erreur réseau: ${t.message}"
                    }

                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    afficherMessageCanauxVides()
                }
            })
    }

    private fun afficherCanaux(canaux: List<Canal>) {
        // Vider le conteneur des canaux
        canauxContainer.removeAllViews()

        if (canaux.isEmpty()) {
            afficherMessageCanauxVides()
            return
        }

        // Ajouter chaque canal
        for (canal in canaux) {
            try {
                val canalView = layoutInflater.inflate(
                    R.layout.item_channel,
                    canauxContainer,
                    false
                ) as TextView

                // Configurer la vue du canal
                canalView.text = "# ${canal.nom}"
                canalView.tag = canal.id

                // Debugger l'ID du canal
                Log.d(TAG, "Ajout du canal: id=${canal.id}, nom=${canal.nom}")

                // Configurer le clic sur le canal
                canalView.setOnClickListener {
                    Log.d(TAG, "Clic sur canal: id=${canal.id}, nom=${canal.nom}")
                    ouvrirCanal(canal.id, canal.nom)
                }

                // Ajouter la vue au conteneur
                canauxContainer.addView(canalView)
            } catch (e: Exception) {
                Log.e(TAG, "Erreur lors de l'ajout du canal ${canal.id}", e)
            }
        }
    }

    private fun afficherMessageCanauxVides(message: String = "Aucun canal disponible dans ce workspace") {
        try {
            if (!isAdded) return

            // Vider le conteneur des canaux
            canauxContainer.removeAllViews()

            // Créer et afficher un message d'absence de canaux
            val emptyMessageView = TextView(context)
            emptyMessageView.text = message
            emptyMessageView.textSize = 16f
            emptyMessageView.setPadding(16, 32, 16, 16)
            emptyMessageView.setTextColor(resources.getColor(android.R.color.white, null))

            // Ajouter la vue au conteneur
            canauxContainer.addView(emptyMessageView)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'affichage du message canaux vides", e)
        }
    }

    private fun ouvrirCanal(canalId: String, canalNom: String) {
        if (canalId.isEmpty()) {
            Log.e(TAG, "Impossible d'ouvrir le canal: ID vide")
            Toast.makeText(context, "Erreur: ID du canal invalide", Toast.LENGTH_SHORT).show()
            return
        }

        // Assurer que workspaceId n'est pas null avant d'ouvrir le chat
        if (workspaceId.isNullOrEmpty()) {
            Log.e(TAG, "Impossible d'ouvrir le canal: ID du workspace manquant")
            Toast.makeText(context, "Erreur: ID du workspace manquant", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Ouverture du canal: id=$canalId, nom=$canalNom, workspaceId=$workspaceId")

        // Utiliser l'activité parente pour ouvrir le chat
        (activity as? HomeActivity)?.openChat(canalId, canalNom)
    }
}