package com.example.bibliounifornew.features.usuario.livro

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bibliounifornew.R
import com.example.bibliounifornew.data.BibliotecaOnlineRepository
import com.example.bibliounifornew.data.EntidadeLivro
import com.example.bibliounifornew.features.usuario.solicitacao.TelaRF19SolicitacoesTermosCondicoes
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TelaRF11_1_ResultadoPesquisa : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LivroAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.telarf11_1_resultado_pesquisa)

        val termoPesquisa = intent.getStringExtra("TERMO_PESQUISA") ?: ""
        val filtroTitulo  = intent.getStringExtra("FILTRO_TITULO") ?: ""
        val filtroAutor   = intent.getStringExtra("FILTRO_AUTOR") ?: ""
        val filtroCat     = intent.getStringExtra("FILTRO_CATEGORIA") ?: ""
        val filtroDisp    = intent.getStringExtra("FILTRO_DISPONIBILIDADE") ?: "Todos"

        val textResultado = findViewById<TextView>(R.id.textResultadoTitulo)
        textResultado.text = "Resultado: \"${termoPesquisa.ifEmpty { "Filtros aplicados" }}\""

        configurarRecyclerView()
        realizarBusca(termoPesquisa, filtroTitulo, filtroAutor, filtroCat, filtroDisp)

        // Busca complementar na nuvem (Google Books API) para enriquecer o acervo local
        if (termoPesquisa.isNotEmpty()) {
            buscarNaNuvem(termoPesquisa)
        } else {
            val catTodos = getString(R.string.categoria_todos)
            if (filtroCat.isNotEmpty() && filtroCat != catTodos && filtroCat != "Todas as Categorias") {
                buscarNaNuvem(filtroCat)
            }
        }
    }

    // ─── RECYCLER ──────────────────────────────────────────────────────────────

    private fun configurarRecyclerView() {
        recyclerView = findViewById(R.id.recyclerViewResultados)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = LivroAdapter(
            livros        = mutableListOf(),
            onItemClick   = { livro ->
                startActivity(
                    Intent(this, TelaRF12TelaDoLivro::class.java)
                        .putExtra("LIVRO_ID", livro.id)
                )
            },
            onSuaLivraria = { livro -> adicionarSuaLivraria(livro) },
            // Aluguel passa obrigatoriamente pelos Termos e Condições (RF19)
            onAlugarLivro = { livro -> irParaTermosAluguel(livro) }
        )

        recyclerView.adapter = adapter
    }

    // ─── ALUGUEL → RF19 ────────────────────────────────────────────────────────

    private fun irParaTermosAluguel(livro: EntidadeLivro) {
        startActivity(
            Intent(this, TelaRF19SolicitacoesTermosCondicoes::class.java).apply {
                putExtra("TIPO_MIDIA", "Aluguel")
                putExtra("LIVRO_ID",   livro.id)
                putExtra("TITULO",     livro.title)
                putExtra("AUTOR",      livro.author)
            }
        )
    }

    // ─── LIVRARIA ──────────────────────────────────────────────────────────────

    private fun adicionarSuaLivraria(livro: EntidadeLivro) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, getString(R.string.erro_login_para_livraria), Toast.LENGTH_SHORT).show()
            return
        }
        val dados = hashMapOf(
            "usuarioId"     to uid,
            "livroId"       to livro.id,
            "titulo"        to livro.title,
            "autor"         to livro.author,
            "coverUrl"      to livro.coverUrl,
            "statusLeitura" to "Não Lido",
            "adicionadoEm"  to System.currentTimeMillis()
        )
        FirebaseFirestore.getInstance()
            .collection("biblioteca_usuarios")
            .document("${uid}_${livro.id}")
            .set(dados, SetOptions.merge())
            .addOnSuccessListener {
                Snackbar.make(recyclerView, "Livro adicionado à sua livraria.", Snackbar.LENGTH_LONG)
                    .setBackgroundTint(Color.parseColor("#444444"))
                    .setTextColor(Color.WHITE)
                    .show()
            }
            .addOnFailureListener {
                Toast.makeText(this, getString(R.string.erro_adicionar_livraria_pesquisa), Toast.LENGTH_SHORT).show()
            }
    }

    // ─── BUSCA COM ISBN-AWARENESS ──────────────────────────────────────────────

    /**
     * Remove hífens/espaços de um possível ISBN para comparação.
     */
    private fun normalizarIsbn(s: String) = s.replace("-", "").replace(" ", "")

    /**
     * Retorna true se o termo (após normalização) representa um ISBN-10:
     * 10 caracteres, todos dígitos (com X maiúsculo/minúsculo permitido na última posição).
     */
    private fun isIsbn10(s: String): Boolean {
        val n = normalizarIsbn(s)
        if (n.length != 10) return false
        return n.dropLast(1).all { it.isDigit() } &&
               (n.last().isDigit() || n.last().uppercaseChar() == 'X')
    }

    /**
     * Retorna true se o termo (após normalização) representa um ISBN-13:
     * 13 caracteres, todos dígitos.
     */
    private fun isIsbn13(s: String): Boolean {
        val n = normalizarIsbn(s)
        return n.length == 13 && n.all { it.isDigit() }
    }

    private fun realizarBusca(
        termo: String,
        fTitulo: String = "",
        fAutor: String  = "",
        fCat: String    = "",
        fDisp: String   = "Todos"
    ) {
        val firestore  = FirebaseFirestore.getInstance()
        val termoTrim  = termo.trim()
        val termoLower = termoTrim.lowercase()
        val titLower   = fTitulo.lowercase().trim()
        val autLower   = fAutor.lowercase().trim()

        // Detecta o modo de busca antes de consultar o Firestore
        val buscaIsbn10 = isIsbn10(termoTrim)
        val buscaIsbn13 = isIsbn13(termoTrim)
        val isbnNorm    = normalizarIsbn(termoTrim).lowercase()

        val catTodos = try { getString(R.string.categoria_todos) } catch (e: Exception) { "Todas as Categorias" }
        val filtroCatTratado = if (fCat.isEmpty()) catTodos else fCat
        val ignorarCat = filtroCatTratado.equals(catTodos, ignoreCase = true) ||
                         filtroCatTratado.equals("Todas as Categorias", ignoreCase = true) ||
                         filtroCatTratado.equals("Todas", ignoreCase = true)

        firestore.collection("livros")
            .get()
            .addOnSuccessListener { result ->
                val listaDeLivros = mutableListOf<EntidadeLivro>()
                Log.d("BUSCA", "Firestore retornou ${result.size()} documentos. Termo: '$termoTrim'")

                for (document in result) {
                    try {
                        val id       = document.id
                        val title    = (document.getString("title")    ?: document.getString("titulo")    ?: "").trim()
                        val author   = (document.getString("author")   ?: document.getString("autor")     ?: "").trim()
                        val category = (document.getString("category") ?: document.getString("categoria") ?: "Outros").trim()
                        val coverUrl = document.getString("coverUrl") ?: ""
                        val isbn10   = normalizarIsbn(document.getString("isbn10") ?: document.getString("isbn_10") ?: "")
                        val isbn13   = normalizarIsbn(document.getString("isbn13") ?: document.getString("isbn_13") ?: "")
                        val publishDate = document.getString("publishDate") ?: document.getString("data") ?: ""

                        val stockVal = document.get("estoque") ?: document.get("quantidade") ?: document.get("stock")
                        val stock = when (stockVal) {
                            is Long   -> stockVal
                            is Int    -> stockVal.toLong()
                            is String -> stockVal.toLongOrNull()
                            else      -> null
                        }
                        val isAvailable = document.getBoolean("isAvailable") ?: (stock?.let { it > 0 } ?: true)

                        // ── MATCH PRINCIPAL ────────────────────────────────────
                        // Se o termo for ISBN válido → match EXATO no campo correspondente.
                        // Se for texto → match de substring em título ou autor.
                        val matchTermo = when {
                            termoTrim.isEmpty() -> true
                            buscaIsbn10 -> isbn10.equals(isbnNorm, ignoreCase = true)
                            buscaIsbn13 -> isbn13.equals(isbnNorm, ignoreCase = true)
                            else -> title.lowercase().contains(termoLower) ||
                                    author.lowercase().contains(termoLower)
                        }

                        // ── FILTROS ADICIONAIS ─────────────────────────────────
                        val matchTitulo = titLower.isEmpty() || title.lowercase().contains(titLower)
                        val matchAutor  = autLower.isEmpty() || author.lowercase().contains(autLower)

                        val catLower   = category.lowercase()
                        val fCatLower  = filtroCatTratado.lowercase()
                        val matchCat   = ignorarCat || catLower.contains(fCatLower) || matchSinonimoCat(catLower, fCatLower)

                        val matchDisp = when (fDisp) {
                            "Disponível"   -> isAvailable
                            "Indisponível" -> !isAvailable
                            else           -> true
                        }

                        if (matchTermo && matchTitulo && matchAutor && matchCat && matchDisp) {
                            listaDeLivros.add(
                                EntidadeLivro(
                                    id          = id,
                                    title       = title,
                                    author      = author,
                                    category    = category,
                                    coverUrl    = coverUrl,
                                    isbn10      = isbn10,
                                    isbn13      = isbn13,
                                    isAvailable = isAvailable,
                                    publishDate = publishDate
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("BUSCA", "Erro ao processar livro ${document.id}", e)
                    }
                }

                adapter.updateData(listaDeLivros)
                Log.d("BUSCA", "Total exibido após filtros: ${listaDeLivros.size}")
            }
            .addOnFailureListener { e ->
                Log.e("BUSCA", "Erro Firestore", e)
            }
    }

    /**
     * Sinônimos de categoria — extraído de realizarBusca para manter o método legível.
     */
    private fun matchSinonimoCat(catLower: String, fCatLower: String): Boolean {
        val mapa = mapOf(
            "tecnologia" to listOf(
                "programação", "computação", "computer science", "software", "ti", "desenvolvimento",
                "programming", "computing", "informática", "web", "android", "ios", "java", "python",
                "javascript", "cloud", "aws", "azure", "docker", "agile", "scrum", "devops", "segurança",
                "cybersecurity", "hacking", "banco de dados", "sql", "nosql", "artificial intelligence",
                "inteligência artificial", "ai", "ia", "machine learning", "frontend", "backend",
                "fullstack", "data science", "hardware", "internet", "digital", "algoritmos", "coding",
                "networks", "redes"
            ),
            "fantasia" to listOf(
                "fantasia épica", "fantasia juvenil", "ficção científica", "science fiction", "sci-fi",
                "distopia", "fantasy", "magic", "magia", "dragons", "dragões", "bruxaria", "witchcraft",
                "vampiro", "lobisomem", "zumbi", "mitologia", "mythology", "steampunk", "cyberpunk",
                "space opera", "alien", "universo", "medieval", "espada", "feitiçaria"
            ),
            "suspense" to listOf(
                "thriller", "mistério", "mistério policial", "crime", "mystery", "police", "terror",
                "horror", "investigação", "detetive", "noir", "psicológico", "spy", "espionagem",
                "assassinato", "murder", "true crime", "sobrenatural", "paranormal"
            ),
            "romance" to listOf(
                "drama", "romance histórico", "romance contemporâneo", "love", "romantic", "amor",
                "paixão", "comédia romântica", "rom-com", "new adult", "young adult", "ya",
                "erótico", "sentimental"
            ),
            "literatura" to listOf(
                "fiction", "literature", "poetry", "clássico", "classic", "contos", "short stories",
                "prosa", "antologia"
            ),
            "ciência" to listOf(
                "science", "nature", "math", "biologia", "física", "química", "astronomia",
                "matemática", "physics", "chemistry", "biology", "astronomy", "mathematics",
                "pesquisa", "research", "evolução", "evolution"
            ),
            "ciencia" to listOf(
                "science", "nature", "math", "biologia", "física", "química", "astronomia",
                "matemática", "physics", "chemistry", "biology", "astronomy", "mathematics",
                "pesquisa", "research", "evolução", "evolution"
            ),
            "história" to listOf(
                "history", "arqueologia", "archaeology", "guerra", "war", "civilização",
                "biografia histórica"
            ),
            "historia" to listOf(
                "history", "arqueologia", "archaeology", "guerra", "war", "civilização",
                "biografia histórica"
            ),
            "biografia" to listOf(
                "biography", "autobiography", "memoir", "memórias", "vida de"
            )
        )
        val sinonimos = mapa[fCatLower] ?: return fCatLower == "outros"
        return sinonimos.any { catLower.contains(it) }
    }

    // ─── BUSCA NA NUVEM (Google Books) ────────────────────────────────────────

    private fun buscarNaNuvem(termoParaApi: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val repository = BibliotecaOnlineRepository()
                repository.buscarEImportarLivro(
                    termoDeBusca = termoParaApi,
                    onSuccess = {
                        lifecycleScope.launch(Dispatchers.Main) {
                            Toast.makeText(
                                this@TelaRF11_1_ResultadoPesquisa,
                                getString(R.string.msg_buscando_novidades),
                                Toast.LENGTH_SHORT
                            ).show()
                            delay(1000)
                            val originalTermo = intent.getStringExtra("TERMO_PESQUISA") ?: ""
                            val fTitulo = intent.getStringExtra("FILTRO_TITULO") ?: ""
                            val fAutor  = intent.getStringExtra("FILTRO_AUTOR")  ?: ""
                            val fCat    = intent.getStringExtra("FILTRO_CATEGORIA") ?: ""
                            val fDisp   = intent.getStringExtra("FILTRO_DISPONIBILIDADE") ?: "Todos"
                            realizarBusca(originalTermo, fTitulo, fAutor, fCat, fDisp)
                        }
                    },
                    onFailure = { erro ->
                        Log.d("API_LIVROS", "Google Books finalizado: ${erro.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e("API_LIVROS", "Erro na busca online", e)
            }
        }
    }
}
