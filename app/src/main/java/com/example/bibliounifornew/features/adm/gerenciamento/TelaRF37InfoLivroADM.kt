package com.example.bibliounifornew.features.adm.gerenciamento

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.example.bibliounifornew.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class TelaRF37InfoLivroADM : AppCompatActivity() {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var livroId: String = ""

    // Estado de estoque mantido em memória para evitar re-leituras constantes
    private var quantidadeDisponivel: Long = 0L
    private var totalExemplares     : Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.telarf37_info_livro_adm)

        livroId = intent.getStringExtra("LIVRO_ID") ?: ""
        if (livroId.isEmpty()) {
            Toast.makeText(this, getString(R.string.erro_id_livro_nao_fornecido), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        carregarDadosLivro()

        // ─── LÁPIS → habilitar campo individual ──────────────────────────────
        configurarEdicao(R.id.btnEditTitulo,        R.id.editTituloLivro,    "title")
        configurarEdicao(R.id.btnEditAutor,         R.id.editAutorLivro,     "author")
        configurarEdicao(R.id.btnEditDescricao,     R.id.editDescricaoLivro, "description")
        configurarEdicao(R.id.btnEditLingua,        R.id.editLingua,         "language")
        configurarEdicao(R.id.btnEditEditora,       R.id.editEditora,        "publisher")
        configurarEdicao(R.id.btnEditDimensao,      R.id.editDimensao,       "dimensions")
        configurarEdicao(R.id.btnEditISBN10,        R.id.editISBN10,         "isbn10")
        configurarEdicao(R.id.btnEditISBN13,        R.id.editISBN13,         "isbn13")
        configurarEdicao(R.id.btnEditASIN,          R.id.editASIN,           "asin")
        configurarEdicao(R.id.btnEditData,          R.id.editData,           "publishDate")
        configurarEdicao(R.id.btnEditPaginas,       R.id.editPaginas,        "totalPages")
        configurarEdicao(R.id.btnEditCategoria,     R.id.editCategoria,      "category")
        configurarEdicao(R.id.btnEditLinkCapa,      R.id.editLinkCapa,       "coverUrl")
        configurarEdicao(R.id.btnEditLinkPdf,       R.id.editLinkPdf,        "linkPdf")
        configurarEdicao(R.id.btnEditLinkAudiobook, R.id.editLinkAudiobook,  "linkAudiobook")

        // ─── CONTROLE DE EXEMPLARES ──────────────────────────────────────────
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.btnDiminuirExemplares)
            ?.setOnClickListener { atualizarExemplares(-1) }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.btnAumentarExemplares)
            ?.setOnClickListener { atualizarExemplares(1) }

        // ─── SALVAR TUDO ─────────────────────────────────────────────────────
        findViewById<MaterialButton>(R.id.btnSalvarModificacoes)?.setOnClickListener {
            salvarTodasModificacoes()
        }

        // ─── APAGAR MÍDIA ────────────────────────────────────────────────────
        findViewById<Button>(R.id.btnApagarMidia)?.setOnClickListener {
            abrirPopupApagar()
        }
    }

    // ─── CARREGAR DADOS ───────────────────────────────────────────────────────

    private fun carregarDadosLivro() {
        db.collection("livros").document(livroId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener

                fun str(vararg keys: String) =
                    keys.mapNotNull { doc.getString(it)?.takeIf { v -> v.isNotEmpty() } }
                        .firstOrNull() ?: ""

                fun lng(vararg keys: String) =
                    keys.mapNotNull { doc.getLong(it) }.firstOrNull() ?: 0L

                // Campos de texto
                setField(R.id.editTituloLivro,    str("title", "titulo"))
                setField(R.id.editAutorLivro,     str("author", "autor"))
                setField(R.id.editDescricaoLivro, str("description", "descricao"))
                setField(R.id.editLingua,         str("language", "lingua").ifEmpty { "Português" })
                setField(R.id.editEditora,        str("publisher", "editora"))
                setField(R.id.editDimensao,       str("dimensions", "dimensoes", "dimensao"))
                setField(R.id.editISBN10,         str("isbn10", "isbn_10", "ISBN10"))
                setField(R.id.editISBN13,         str("isbn13", "isbn_13", "ISBN13"))
                setField(R.id.editASIN,           str("asin", "ASIN"))
                setField(R.id.editData,           str("publishDate", "publishedDate", "dataPublicacao"))
                setField(R.id.editPaginas,        lng("totalPages", "pageCount", "paginas").let {
                    if (it > 0) it.toString() else "0"
                })
                setField(R.id.editCategoria,      str("category", "categoria"))
                setField(R.id.editLinkCapa,       str("coverUrl", "imagemUrl"))
                setField(R.id.editLinkPdf,        str("linkPdf"))
                setField(R.id.editLinkAudiobook,  str("linkAudiobook"))

                // Braille — define estado antes de registrar listener
                val hasBraille = doc.getBoolean("hasBraille") ?: doc.getBoolean("braille") ?: false
                val checkBraille = findViewById<CheckBox>(R.id.checkBrailleInfo)
                checkBraille?.isChecked = hasBraille

                // ── ESTOQUE X/Y ──────────────────────────────────────────────
                // quantidade = disponíveis em tempo real (decrementado pelo repo de aluguel)
                // totalExemplares = total físico que a faculdade possui
                quantidadeDisponivel = lng("quantidade", "estoque", "exemplares")
                totalExemplares      = lng("totalExemplares", "stockQuantity")
                    .takeIf { it > 0L } ?: quantidadeDisponivel

                atualizarDisplayEstoque()

                // Capa: fallback neutro ic_sem_capa ao invés do livro do Tolkien (osda)
                val coverUrl = str("coverUrl", "imagemUrl")
                findViewById<ImageView>(R.id.imgCapaLivroDetalhe)?.load(
                    coverUrl.ifEmpty { null }
                ) {
                    placeholder(R.drawable.ic_sem_capa)
                    error(R.drawable.ic_sem_capa)
                    fallback(R.drawable.ic_sem_capa)
                }

                carregarAvaliacoes()
            }
            .addOnFailureListener {
                Toast.makeText(this, getString(R.string.erro_conexao_banco), Toast.LENGTH_SHORT).show()
            }
    }

    private fun setField(viewId: Int, texto: String) {
        findViewById<EditText>(viewId)?.setText(texto)
    }

    // ─── DISPLAY DE ESTOQUE X/Y ───────────────────────────────────────────────

    private fun atualizarDisplayEstoque() {
        val disponivel = quantidadeDisponivel.coerceAtLeast(0L)
        val total      = totalExemplares.coerceAtLeast(disponivel)
        // Exibe "disp/total disponíveis" no label visual
        findViewById<TextView>(R.id.textExemplaresFormato)?.text =
            getString(R.string.fmt_exemplares_ratio, disponivel.toInt(), total.toInt())
        // Exibe só o total no controle de stepper +/-
        findViewById<TextView>(R.id.textExemplaresTotal)?.text = total.toString()
    }

    // ─── AVALIAÇÕES ───────────────────────────────────────────────────────────

    private fun carregarAvaliacoes() {
        val container        = findViewById<LinearLayout>(R.id.containerAvaliacoes)
        val textSemAvaliacoes = findViewById<TextView>(R.id.textSemAvaliacoes)
        container?.let { ll ->
            for (i in ll.childCount - 1 downTo 0) {
                val child = ll.getChildAt(i)
                if (child.id != R.id.textSemAvaliacoes) ll.removeView(child)
            }
        }
        textSemAvaliacoes?.visibility = View.VISIBLE
    }

    // ─── EDIÇÃO INDIVIDUAL POR LÁPIS ─────────────────────────────────────────

    private fun configurarEdicao(botaoId: Int, campoId: Int, firestoreField: String) {
        val botao = findViewById<ImageView>(botaoId)
        val campo = findViewById<EditText>(campoId) ?: return

        botao?.setOnClickListener {
            if (!campo.isEnabled) {
                campo.isEnabled = true
                campo.requestFocus()
                campo.setSelection(campo.text.length)
                botao.setImageResource(android.R.drawable.ic_menu_save)
                Toast.makeText(this, getString(R.string.msg_editando), Toast.LENGTH_SHORT).show()
            } else {
                val novoValor = campo.text.toString()
                db.collection("livros").document(livroId)
                    .update(firestoreField, novoValor)
                    .addOnSuccessListener {
                        campo.isEnabled = false
                        botao.setImageResource(R.drawable.ic_edit_pencil)
                        Toast.makeText(this, getString(R.string.msg_campo_atualizado), Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, getString(R.string.erro_conexao_banco), Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    // ─── SALVAR TODAS AS MODIFICAÇÕES (batch update) ──────────────────────────

    private fun salvarTodasModificacoes() {
        fun textOf(id: Int) = findViewById<EditText>(id)?.text?.toString()?.trim() ?: ""
        fun longOf(id: Int) = textOf(id).toLongOrNull()

        val hasBraille = findViewById<CheckBox>(R.id.checkBrailleInfo)?.isChecked ?: false

        // Garante que campos numéricos não chegam nulos ao Firestore
        val paginas = longOf(R.id.editPaginas) ?: 0L

        val campos = mutableMapOf<String, Any>(
            // Campos texto — dois aliases por campo para manter compatibilidade com o schema
            "title"         to textOf(R.id.editTituloLivro),
            "author"        to textOf(R.id.editAutorLivro),
            "description"   to textOf(R.id.editDescricaoLivro),
            "language"      to textOf(R.id.editLingua),
            "lingua"        to textOf(R.id.editLingua),
            "publisher"     to textOf(R.id.editEditora),
            "editora"       to textOf(R.id.editEditora),
            "dimensions"    to textOf(R.id.editDimensao),
            "isbn10"        to textOf(R.id.editISBN10),
            "isbn13"        to textOf(R.id.editISBN13),
            "asin"          to textOf(R.id.editASIN),
            "publishDate"   to textOf(R.id.editData),
            "publishedDate" to textOf(R.id.editData),
            "totalPages"    to paginas,
            "pageCount"     to paginas,
            "category"      to textOf(R.id.editCategoria),
            "categoria"     to textOf(R.id.editCategoria),
            "coverUrl"      to textOf(R.id.editLinkCapa),
            "linkPdf"       to textOf(R.id.editLinkPdf),
            "linkAudiobook" to textOf(R.id.editLinkAudiobook),
            "hasBraille"    to hasBraille,
            "braille"       to hasBraille
        )

        val btn = findViewById<MaterialButton>(R.id.btnSalvarModificacoes)
        btn?.isEnabled = false
        btn?.text = getString(R.string.msg_salvando)

        db.collection("livros").document(livroId)
            .update(campos)
            .addOnSuccessListener {
                btn?.isEnabled = true
                btn?.text = getString(R.string.btn_salvar_modificacoes)
                Toast.makeText(this, getString(R.string.msg_modificacoes_salvas), Toast.LENGTH_SHORT).show()

                // Atualiza a capa se o link foi alterado
                val novoLinkCapa = textOf(R.id.editLinkCapa)
                findViewById<ImageView>(R.id.imgCapaLivroDetalhe)?.load(
                    novoLinkCapa.ifEmpty { null }
                ) {
                    placeholder(R.drawable.ic_sem_capa)
                    error(R.drawable.ic_sem_capa)
                    fallback(R.drawable.ic_sem_capa)
                }
            }
            .addOnFailureListener {
                btn?.isEnabled = true
                btn?.text = getString(R.string.btn_salvar_modificacoes)
                Toast.makeText(this, getString(R.string.erro_conexao_banco), Toast.LENGTH_SHORT).show()
            }
    }

    // ─── CONTROLE DE EXEMPLARES (+/-) ─────────────────────────────────────────
    //
    // Semântica: o stepper controla o totalExemplares (Y).
    // Ao aumentar o total, a diferença é somada aos disponíveis (mais cópias chegaram).
    // Ao diminuir, se disponiveis > novo_total, disponíveis é clampeado ao total.

    private fun atualizarExemplares(delta: Int) {
        val novoTotal = (totalExemplares + delta).coerceAtLeast(0L)
        val delta64   = novoTotal - totalExemplares

        // Disponíveis sobem quando chegam novas cópias; ao reduzir, não ultrapassam o total
        val novoDisponivel = (quantidadeDisponivel + delta64).coerceAtLeast(0L).coerceAtMost(novoTotal)

        val campos = mapOf(
            "totalExemplares" to novoTotal,
            "stockQuantity"   to novoTotal,
            "quantidade"      to novoDisponivel,
            "estoque"         to novoDisponivel,
            "exemplares"      to novoDisponivel
        )

        db.collection("livros").document(livroId)
            .update(campos)
            .addOnSuccessListener {
                totalExemplares      = novoTotal
                quantidadeDisponivel = novoDisponivel
                atualizarDisplayEstoque()
            }
            .addOnFailureListener {
                Toast.makeText(this, getString(R.string.erro_conexao_banco), Toast.LENGTH_SHORT).show()
            }
    }

    // ─── POPUP APAGAR ─────────────────────────────────────────────────────────

    private fun abrirPopupApagar() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.popup_apagar_midia)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val inputSenha   = dialog.findViewById<TextInputEditText>(R.id.editSenhaAtual)
        val textErro     = dialog.findViewById<TextView>(R.id.textErroSenha)
        val btnConfirmar = dialog.findViewById<MaterialButton>(R.id.btnConfirmarApagar)
        val btnCancelar  = dialog.findViewById<MaterialButton>(R.id.btnCancelarApagar)
        textErro?.visibility = View.GONE

        btnConfirmar?.setOnClickListener {
            val senha = inputSenha?.text.toString().trim()
            if (senha.isEmpty()) {
                textErro?.visibility = View.VISIBLE
                textErro?.text = getString(R.string.popup_apagar_midia_erro_senha)
                return@setOnClickListener
            }

            val user = auth.currentUser
            if (user == null || user.email.isNullOrEmpty()) {
                Toast.makeText(this, getString(R.string.erro_sessao_expirada), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnConfirmar.isEnabled = false
            textErro?.visibility = View.GONE

            val credential = EmailAuthProvider.getCredential(user.email!!, senha)
            user.reauthenticate(credential)
                .addOnSuccessListener {
                    db.collection("livros").document(livroId).delete()
                        .addOnSuccessListener {
                            Toast.makeText(this, getString(R.string.msg_midia_removida), Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            finish()
                        }
                        .addOnFailureListener {
                            btnConfirmar.isEnabled = true
                            Toast.makeText(this, getString(R.string.erro_conexao_banco), Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener {
                    btnConfirmar.isEnabled = true
                    textErro?.visibility = View.VISIBLE
                    textErro?.text = getString(R.string.erro_senha_incorreta)
                }
        }

        btnCancelar?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
