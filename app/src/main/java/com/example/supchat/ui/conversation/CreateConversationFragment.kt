package com.example.supchat.ui.conversation

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.adapters.SelectedParticipantsAdapter
import com.example.supchat.adapters.UserSearchAdapter
import com.example.supchat.api.ApiClient
import com.example.supchat.models.response.UserSearchData
import com.example.supchat.models.response.UserSearchResponse
import com.example.supchat.models.response.messageprivate.ConversationDetailsResponse
import com.example.supchat.ui.chat.PrivateConversationFragment
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class CreateConversationFragment : Fragment(), UserSearchAdapter.OnUserClickListener {

    companion object {
        private const val TAG = "CreateConversation"

        fun newInstance(): CreateConversationFragment {
            return CreateConversationFragment()
        }
    }

    // Vues
    private lateinit var isGroupCheckbox: CheckBox
    private lateinit var searchInput: TextInputEditText
    private lateinit var searchRecyclerView: RecyclerView
    private lateinit var selectedParticipantsRecyclerView: RecyclerView
    private lateinit var createButton: Button
    private lateinit var cancelButton: Button
    private lateinit var setGroupNameButton: Button
    private lateinit var groupNameDisplay: TextView

    // Adaptateurs
    private lateinit var searchAdapter: UserSearchAdapter
    private lateinit var selectedAdapter: SelectedParticipantsAdapter

    // Données
    private val selectedParticipants = mutableListOf<UserSearchData>()
    private var groupName: String = "" // ✅ AJOUT: Variable pour stocker le nom du groupe

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_create_conversation, container, false)

        initViews(view)
        setupRecyclerViews()
        setupListeners()

        return view
    }

    private fun initViews(view: View) {
        isGroupCheckbox = view.findViewById(R.id.is_group_checkbox)
        searchInput = view.findViewById(R.id.search_users_input)
        searchRecyclerView = view.findViewById(R.id.search_users_recycler_view)
        selectedParticipantsRecyclerView = view.findViewById(R.id.selected_participants_recycler_view)
        createButton = view.findViewById(R.id.create_conversation_button)
        cancelButton = view.findViewById(R.id.cancel_button)
        setGroupNameButton = view.findViewById(R.id.set_group_name_button)
        groupNameDisplay = view.findViewById(R.id.group_name_display)

        // ✅ CORRECTION: Configuration du bouton pour définir le nom du groupe
        setGroupNameButton.setOnClickListener {
            showGroupNameDialog()
        }
    }

    private fun setupRecyclerViews() {
        // Adaptateur pour la recherche
        searchAdapter = UserSearchAdapter(
            context = requireContext(),
            onUserClickListener = this
        )
        searchRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }

        // Adaptateur pour les participants sélectionnés
        selectedAdapter = SelectedParticipantsAdapter(
            context = requireContext(),
            participants = selectedParticipants
        ) { participant ->
            removeParticipant(participant)
        }
        selectedParticipantsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = selectedAdapter
        }
    }

    private fun setupListeners() {
        // Recherche d'utilisateurs
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.length >= 2) {
                    searchUsers(query)
                } else {
                    searchAdapter.updateUsers(emptyList())
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Checkbox groupe
        isGroupCheckbox.setOnCheckedChangeListener { _, isChecked ->
            // ✅ CORRECTION: Afficher/masquer le bouton et l'affichage du nom
            setGroupNameButton.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateGroupNameDisplay()
            updateCreateButtonState()
        }

        // Boutons
        createButton.setOnClickListener { createConversation() }
        cancelButton.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        updateCreateButtonState()
    }

    // ✅ AJOUT: Fonction pour afficher le dialogue de nom de groupe
    private fun showGroupNameDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_group_name, null)

        val groupNameInput: TextInputEditText = dialogView.findViewById(R.id.group_name_input)
        val confirmButton: Button = dialogView.findViewById(R.id.confirm_group_name_button)
        val cancelButton: Button = dialogView.findViewById(R.id.cancel_group_name_button)

        // Pré-remplir avec le nom actuel s'il existe
        groupNameInput.setText(groupName)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        confirmButton.setOnClickListener {
            val newGroupName = groupNameInput.text.toString().trim()
            if (newGroupName.isNotEmpty()) {
                groupName = newGroupName
                updateGroupNameDisplay()
                updateCreateButtonState()
                dialog.dismiss()
            } else {
                Toast.makeText(context, "Veuillez saisir un nom de groupe", Toast.LENGTH_SHORT).show()
            }
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // ✅ AJOUT: Fonction pour mettre à jour l'affichage du nom du groupe
    private fun updateGroupNameDisplay() {
        if (isGroupCheckbox.isChecked && groupName.isNotEmpty()) {
            groupNameDisplay.text = "📝 Nom du groupe: $groupName"
            groupNameDisplay.visibility = View.VISIBLE
        } else {
            groupNameDisplay.visibility = View.GONE
        }
    }

    private fun searchUsers(query: String) {
        val token = getAuthToken()
        if (token.isEmpty()) return

        ApiClient.searchUsers(token, query)
            .enqueue(object : Callback<UserSearchResponse> {
                override fun onResponse(
                    call: Call<UserSearchResponse>,
                    response: Response<UserSearchResponse>
                ) {
                    if (!isAdded) return
                    if (response.isSuccessful) {
                        val users: List<UserSearchData> = response.body()?.data?.users ?: emptyList()
                        val selectedIds = selectedParticipants.map { it.id }.toSet()
                        val filteredUsers = users.filter { user -> !selectedIds.contains(user.id) }
                        searchAdapter.updateUsers(filteredUsers)
                    }
                }

                override fun onFailure(call: Call<UserSearchResponse>, t: Throwable) {
                    if (!isAdded) return
                    Log.e(TAG, "Erreur recherche utilisateurs", t)
                }
            })
    }

    override fun onUserClick(user: UserSearchData) {
        addParticipant(user)
    }

    override fun onActionButtonClick(user: UserSearchData) {
        addParticipant(user)
    }

    private fun addParticipant(user: UserSearchData) {
        if (!selectedParticipants.any { it.id == user.id }) {
            selectedParticipants.add(user)
            selectedAdapter.notifyItemInserted(selectedParticipants.size - 1)
            updateCreateButtonState()

            // Mettre à jour la recherche pour exclure cet utilisateur
            val currentQuery = searchInput.text.toString().trim()
            if (currentQuery.length >= 2) {
                searchUsers(currentQuery)
            }
        }
    }

    private fun removeParticipant(user: UserSearchData) {
        val index = selectedParticipants.indexOfFirst { it.id == user.id }
        if (index != -1) {
            selectedParticipants.removeAt(index)
            selectedAdapter.notifyItemRemoved(index)
            updateCreateButtonState()

            // Mettre à jour la recherche
            val currentQuery = searchInput.text.toString().trim()
            if (currentQuery.length >= 2) {
                searchUsers(currentQuery)
            }
        }
    }

    private fun updateCreateButtonState() {
        val hasParticipants = selectedParticipants.isNotEmpty()
        val isGroup = isGroupCheckbox.isChecked
        // ✅ CORRECTION: Vérifier si un nom de groupe est requis
        val hasName = if (isGroup) groupName.isNotEmpty() else true

        createButton.isEnabled = hasParticipants && hasName
    }

    private fun createConversation() {
        val token = getAuthToken()
        if (token.isEmpty()) {
            Toast.makeText(context, "Session expirée", Toast.LENGTH_SHORT).show()
            return
        }

        val isGroup = isGroupCheckbox.isChecked
        // ✅ CORRECTION: Utiliser la variable groupName correctement
        val nom = if (isGroup) groupName.takeIf { it.isNotEmpty() } else null
        val participantIds = selectedParticipants.map { it.id }

        val progressDialog = ProgressDialog(requireContext()).apply {
            setMessage("Création de la conversation...")
            setCancelable(false)
            show()
        }

        ApiClient.createConversation(token, nom, participantIds, isGroup)
            .enqueue(object : Callback<ConversationDetailsResponse> {
                override fun onResponse(
                    call: Call<ConversationDetailsResponse>,
                    response: Response<ConversationDetailsResponse>
                ) {
                    progressDialog.dismiss()
                    if (!isAdded) return

                    if (response.isSuccessful) {
                        val conversationDetails = response.body()?.data?.conversation
                        if (conversationDetails != null) {
                            Toast.makeText(context, "Conversation créée avec succès", Toast.LENGTH_SHORT).show()

                            // Ouvrir la conversation
                            openConversation(conversationDetails._id, conversationDetails.nom ?: "Conversation")
                        } else {
                            Toast.makeText(context, "Erreur lors de la création", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val errorMessage = when (response.code()) {
                            400 -> "Données invalides"
                            403 -> "Vous n'avez pas l'autorisation"
                            else -> "Erreur lors de la création (${response.code()})"
                        }
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<ConversationDetailsResponse>, t: Throwable) {
                    progressDialog.dismiss()
                    if (!isAdded) return
                    Toast.makeText(context, "Erreur réseau: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    private fun openConversation(conversationId: String, conversationName: String) {
        val myUserId = getCurrentUserId()
        val conversationFragment = PrivateConversationFragment.newInstance(
            conversationId = conversationId,
            otherUserId = "", // Pour les groupes, pas d'autre utilisateur spécifique
            username = conversationName,
            myUserId = myUserId
        )

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_container, conversationFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun getAuthToken(): String {
        return requireActivity().getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE)
            .getString("auth_token", "") ?: ""
    }

    private fun getCurrentUserId(): String {
        return requireActivity().getSharedPreferences("SupChatPrefs", Context.MODE_PRIVATE)
            .getString("user_id", "") ?: ""
    }
}