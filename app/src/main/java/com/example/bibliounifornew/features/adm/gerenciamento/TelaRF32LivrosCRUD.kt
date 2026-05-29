package com.example.bibliounifornew.features.adm.gerenciamento

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bibliounifornew.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class TelaRF32LivrosCRUD : AppCompatActivity() {

    private val db             = FirebaseFirestore.getInstance()
    private lateinit var adapter: LivrosCrudAdapter
    private val listaCompleta  = mutableListOf<ItemLivroAdm>()
    private var txtVazio: TextView? = null
    private var firestoreListener: ListenerRegistration? = null

    // Estados dos filtros para persistência durante atualizações do banco
    private var termoBuscaBarra: String = ""
    private var filtroAvTitulo: String = ""
    private var filtroAvAutor: String  = ""
    private var filtroAvIsbn: String   = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.telarf32_livroscrud)

        txtVazio = findViewById(R.id.txtAcervoVazio)

        // ─── RECYCLERVIEW ─────────────────────────────────────────────────────
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewMidias)
        adapter = LivrosCrudAdapter(
            lista    = mutableListOf(),
            onEditar = { item ->
                val intent = Intent(this, TelaRF37InfoLivroADM::class.java)
                intent.putExtra("LIVRO_ID", item.docId)
                startActivity(intent)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // ─── BOTÃO ADICIONAR MÍDIA ────────────────────────────────────────────
        findViewById<Button>(R.id.btnAdicionarMidia)?.setOnClickListener {
            startActivity(Intent(this, TelaRF33CadastroLivro::class.java))
        }

        // ─── BUSCA EM TEMPO REAL ──────────────────────────────────────────────
        findViewById<EditText>(R.id.etProcurarMidia)?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filtrarLista(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // ─── FILTRO ───────────────────────────────────────────────────────────
        findViewById<ImageView>(R.id.btnFiltro)?.setOnClickListener {
            abrirPopupFiltro()
        }

        NavigationHelperADM.configurarBarraNavegacao(this)
    }

    /**
     * Recarrega a lista ao retornar de cadastro ou edição,
     * garantindo que novos livros e alterações apareçam sem reiniciar o app.
     */
    override fun onResume() {
        super.onResume()
        carregarLivros()
    }

    override fun onPause() {
        super.onPause()
        firestoreListener?.remove()
        firestoreListener = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CARREGAMENTO FIRESTORE (Otimizado com Gestão de Estado)
    // ─────────────────────────────────────────────────────────────────────────
    private fun carregarLivros() {
        if (firestoreListener != null) return

        firestoreListener = db.collection("livros")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, getString(R.string.erro_conexao_banco), Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    listaCompleta.clear()
                    for (doc in snapshot) {
                        // disponíveis = cópias sem aluguel ativo (campo decrementado pelo repo)
                        val disponiveis = doc.getLong("quantidade")
                            ?: doc.getLong("estoque")
                            ?: doc.getLong("exemplares")
                            ?: 0L
                        // total = total físico da faculdade (nunca decrementado por aluguéis)
                        val totalFisico = doc.getLong("totalExemplares")
                            ?: doc.getLong("stockQuantity")
                            ?: disponiveis   // fallback: documentos antigos sem o campo

                        listaCompleta.add(
                            ItemLivroAdm(
                                docId                = doc.id,
                                titulo               = doc.getString("title")    ?: doc.getString("titulo")      ?: "Título Indisponível",
                                autor                = doc.getString("author")   ?: doc.getString("autor")       ?: "Autor Desconhecido",
                                isbn                 = doc.getString("isbn")     ?: doc.getString("codigo_isbn") ?: "",
                                quantidadeDisponivel = disponiveis,
                                totalExemplares      = totalFisico,
                                coverUrl             = doc.getString("coverUrl") ?: doc.getString("imagemUrl")   ?: ""
                            )
                        )
                    }
                    // Em vez de atualizar a lista bruta, reaplica os filtros ativos
                    aplicarFiltros()
                }
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FILTRAGEM CENTRALIZADA
    // ─────────────────────────────────────────────────────────────────────────
    private fun filtrarLista(termo: String) {
        termoBuscaBarra = termo.trim().lowercase()
        aplicarFiltros()
    }

    private fun aplicarFiltros() {
        val filtrado = listaCompleta.filter { livro ->
            // 1. Filtro da barra de busca (pesquisa global)
            val matchBusca = termoBuscaBarra.isBlank() ||
                    livro.titulo.lowercase().contains(termoBuscaBarra) ||
                    livro.autor.lowercase().contains(termoBuscaBarra)  ||
                    livro.isbn.contains(termoBuscaBarra)

            // 2. Filtros avançados do popup
            val matchTituloAv = filtroAvTitulo.isBlank() || livro.titulo.lowercase().contains(filtroAvTitulo)
            val matchAutorAv  = filtroAvAutor.isBlank()  || livro.autor.lowercase().contains(filtroAvAutor)
            val matchIsbnAv   = filtroAvIsbn.isBlank()   || livro.isbn.contains(filtroAvIsbn)

            matchBusca && matchTituloAv && matchAutorAv && matchIsbnAv
        }

        adapter.atualizarLista(filtrado)
        txtVazio?.visibility = if (filtrado.isEmpty()) View.VISIBLE else View.GONE
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POPUP FILTRO AVANÇADO (Melhorado)
    // ─────────────────────────────────────────────────────────────────────────
    private fun abrirPopupFiltro() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.popup_filtro_verificar_midia)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val editTitulo = dialog.findViewById<EditText>(R.id.editTituloFiltro)
        val editAutor  = dialog.findViewById<EditText>(R.id.editAutorFiltro)
        val editIsbn   = dialog.findViewById<EditText>(R.id.editISBNFiltro)
        val btnSalvar  = dialog.findViewById<Button>(R.id.buttonSalvarFiltro)
        val btnLimpar  = dialog.findViewById<Button>(R.id.buttonLimparFiltro)

        // IMPORTANTE: Preencher com os valores atuais para não "desalvar" ao abrir
        editTitulo?.setText(filtroAvTitulo)
        editAutor?.setText(filtroAvAutor)
        editIsbn?.setText(filtroAvIsbn)

        btnSalvar?.setOnClickListener {
            // Esconde o teclado antes de fechar para evitar o erro "Ime callback not found"
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)

            filtroAvTitulo = editTitulo?.text.toString().trim().lowercase()
            filtroAvAutor  = editAutor?.text.toString().trim().lowercase()
            filtroAvIsbn   = editIsbn?.text.toString().trim()

            aplicarFiltros()
            Toast.makeText(this, getString(R.string.msg_filtro_aplicado), Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        btnLimpar?.setOnClickListener {
            // Esconde o teclado antes de fechar
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)

            filtroAvTitulo = ""
            filtroAvAutor  = ""
            filtroAvIsbn   = ""
            
            // Limpar também o EditText visual do popup
            editTitulo?.text?.clear()
            editAutor?.text?.clear()
            editIsbn?.text?.clear()

            aplicarFiltros()
            Toast.makeText(this, getString(R.string.msg_filtros_limpos), Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
        
        // Ajuste de largura (90% da tela)
        val width = (resources.displayMetrics.widthPixels * 0.9).toInt()
        dialog.window?.setLayout(width, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
    }
}
