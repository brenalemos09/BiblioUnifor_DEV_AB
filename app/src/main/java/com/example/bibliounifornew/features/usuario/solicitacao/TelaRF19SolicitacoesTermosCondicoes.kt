package com.example.bibliounifornew.features.usuario.solicitacao

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bibliounifornew.R
import com.example.bibliounifornew.data.Solicitacao
import com.example.bibliounifornew.data.SolicitacaoRepository
import com.example.bibliounifornew.features.usuario.biblioteca.TelaRF14LeituraActivity
import com.example.bibliounifornew.data.UsuarioRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class TelaRF19SolicitacoesTermosCondicoes : AppCompatActivity() {

    // ─── DEPENDÊNCIAS ────────────────────────────────────────────────────────
    // SolicitacaoRepository encapsula toda a lógica de persistência.
    private val solicitacaoRepository = SolicitacaoRepository()
    private val usuarioRepository     = UsuarioRepository()
    private val auth                  = FirebaseAuth.getInstance()
    private val db                    = FirebaseFirestore.getInstance()

    private var tituloLivro: String = ""
    private var autorLivro: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.telarf19_solicitacoes_termos_condicoes)

        // ─── EXTRAS RECEBIDOS DE TelaRF19Solicitacoes ────────────────────────
        val tipoMidia = intent.getStringExtra("TIPO_MIDIA") ?: ""
        val livroId   = intent.getStringExtra("LIVRO_ID")   ?: ""
        tituloLivro   = intent.getStringExtra("TITULO")     ?: ""
        autorLivro    = intent.getStringExtra("AUTOR")      ?: ""

        // ─── VIEWS ────────────────────────────────────────────────────────────
        val scrollView   = findViewById<ScrollView>(R.id.scrollTermos)
        val checkBox     = findViewById<CheckBox>(R.id.checkTelaAceitarTermos)
        val btnConfirmar = findViewById<Button>(R.id.buttonConfirmarTermosTela)
        val textAviso    = findViewById<TextView>(R.id.textAvisoScroll)

        // Bloqueados até o usuário rolar o scroll até o fim dos termos
        checkBox.isEnabled     = false
        btnConfirmar.isEnabled = false
        btnConfirmar.alpha     = 0.5f

        // ─── LÓGICA DE SCROLL → HABILITA CHECKBOX ────────────────────────────
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            val child = scrollView.getChildAt(0)
            if (child != null) {
                val diff = child.bottom - (scrollView.height + scrollView.scrollY)
                // Se a diferença for pequena, considera que chegou ao fim
                if (diff <= 50) { 
                    checkBox.isEnabled = true
                    textAviso.visibility = android.view.View.GONE
                }
            }
        }

        // ─── CHECKBOX → HABILITA BOTÃO ────────────────────────────────────────
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            btnConfirmar.isEnabled = isChecked
            btnConfirmar.alpha     = if (isChecked) 1.0f else 0.5f
        }

        // ─── CONFIRMAR → PERSISTE NO FIRESTORE ───────────────────────────────
        btnConfirmar.setOnClickListener {
            gravarSolicitacao(tipoMidia, livroId)
        }
    }

    // ─── PERSISTÊNCIA VIA REPOSITORY ─────────────────────────────────────────

    /**
     * Verifica duplicata e grava a solicitação de forma segura.
     *
     * Passo 1 (IO thread): consulta a coleção pertinente ("solicitacoes_midia" ou "solicitacoes_emprestimo")
     *   para o par uid+livroId. Se já existir, exibe Toast e para.
     *
     * Passo 2 (Main thread): chama o método de gravação no Repository.
     *
     * @param tipoMidia "PDF" | "Braille" | "Audiobook" | "Reserva" | "Aluguel"
     * @param livroId   ID do documento na coleção "livros"
     */
    private fun gravarSolicitacao(tipoMidia: String, livroId: String) {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrEmpty()) {
            Toast.makeText(this, "Faça login para solicitar.", Toast.LENGTH_SHORT).show()
            return
        }

        val btnConfirmar = findViewById<Button>(R.id.buttonConfirmarTermosTela)
        btnConfirmar?.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // ── DEDUPLICATION CHECK ───────────────────────────────────────
                val colecao = if (tipoMidia == "Aluguel") "solicitacoes_emprestimo" else "solicitacoes_midia"
                val campoUid = if (tipoMidia == "Aluguel") "uidAluno" else "uidUsuario"
                val statuses = if (tipoMidia == "Aluguel") listOf("pendente", "ativo", "atrasado") else listOf("pendente", "em_andamento")

                val duplicata = db.collection(colecao)
                    .whereEqualTo(campoUid, uid)
                    .whereEqualTo("idLivro", livroId)
                    .whereIn("status", statuses)
                    .get()
                    .await()

                if (!duplicata.isEmpty) {
                    withContext(Dispatchers.Main) {
                        if (isFinishing || isDestroyed) return@withContext
                        btnConfirmar?.isEnabled = true
                        val msg = if (tipoMidia == "Aluguel") getString(R.string.msg_livro_ja_alugado) else getString(R.string.msg_solicitacao_duplicada)
                        Toast.makeText(this@TelaRF19SolicitacoesTermosCondicoes, msg, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // ── GRAVAR NO FIRESTORE ───────────────────────────────────────
                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) return@withContext

                    if (tipoMidia == "Aluguel") {
                        val resultado = solicitacaoRepository.criarEmprestimoComControleDeEstoque(
                            uidAluno = uid,
                            livroId  = livroId,
                            titulo   = tituloLivro,
                            autor    = autorLivro
                        )

                        resultado.onSuccess {
                            usuarioRepository.registrarNoHistorico(uid, livroId, tituloLivro, autorLivro, "Aluguel Solicitado")

                            // ── 1. Salva referência do livro na subcoleção do usuário ──────────
                            // Caminho: usuarios/{uid}/livros_alugados/{livroId}
                            db.collection("usuarios").document(uid)
                                .collection("livros_alugados")
                                .document(livroId)
                                .set(hashMapOf(
                                    "livroId"     to livroId,
                                    "titulo"      to tituloLivro,
                                    "autor"       to autorLivro,
                                    "dataAluguel" to System.currentTimeMillis()
                                ))

                            // ── 2. Notificação interna ao usuário ─────────────────────────────
                            // Caminho: usuarios/{uid}/notificacoes (lida pelo RF20 Notificações)
                            db.collection("usuarios").document(uid)
                                .collection("notificacoes")
                                .add(hashMapOf(
                                    "titulo"   to "Aluguel solicitado!",
                                    "mensagem" to "\"$tituloLivro\" foi adicionado aos seus aluguéis.",
                                    "livroId"  to livroId,
                                    "lida"     to false,
                                    "data"     to System.currentTimeMillis()
                                ))

                            showPopupSucesso(livroId, true)
                        }.onFailure { e ->
                            btnConfirmar?.isEnabled = true
                            Toast.makeText(this@TelaRF19SolicitacoesTermosCondicoes, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                        }

                    } else {
                        // Fluxo de Mídia (Simples)
                        val solicitacao = Solicitacao(
                            uidUsuario      = uid,
                            uidAluno        = uid,
                            idLivro         = livroId,
                            tipos           = tipoMidia,
                            status          = "pendente",
                            dataSolicitacao = System.currentTimeMillis()
                        )

                        solicitacaoRepository.gravarSolicitacao(solicitacao) { sucesso, _, erro ->
                            if (sucesso) {
                                val acao = when (tipoMidia) {
                                    "Reserva" -> "Reserva Solicitada"
                                    else      -> "$tipoMidia Solicitado"
                                }
                                usuarioRepository.registrarNoHistorico(uid, livroId, tituloLivro, autorLivro, acao)
                                showPopupSucesso(livroId, false)
                            } else {
                                btnConfirmar?.isEnabled = true
                                Toast.makeText(this@TelaRF19SolicitacoesTermosCondicoes, "Erro: $erro", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) return@withContext
                    btnConfirmar?.isEnabled = true
                    Toast.makeText(this@TelaRF19SolicitacoesTermosCondicoes, getString(R.string.erro_conexao_banco), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ─── POPUP DE SUCESSO ─────────────────────────────────────────────────────

    /**
     * Exibe o popup de confirmação.
     * @param isAluguel define qual layout de sucesso exibir (o de aluguel ou o de mídia)
     */
    private fun showPopupSucesso(livroId: String, isAluguel: Boolean) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        
        if (isAluguel) {
            dialog.setContentView(R.layout.popup_livro_adicionado)
            dialog.findViewById<Button>(R.id.buttonVerMeusLivros)?.setOnClickListener {
                dialog.dismiss()
                startActivity(Intent(this, com.example.bibliounifornew.features.usuario.solicitacao.TelaRF18StatusAluguel::class.java))
                finish()
            }
        } else {
            dialog.setContentView(R.layout.telarf19_solicitacoes_voltar_biblioteca)
            dialog.findViewById<Button>(R.id.buttonPopupOkSolicitacao)?.setOnClickListener {
                dialog.dismiss()
                voltarParaLivro(livroId)
            }
        }

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(false)
        dialog.show()
    }

    /**
     * Navegação correta de retorno à tela do livro.
     * Passa o LIVRO_ID explicitamente para que a Activity de destino
     * possa re-popular a UI mesmo que seja recriada pelo CLEAR_TOP.
     */
    private fun voltarParaLivro(livroId: String) {
        val intent = Intent(this, TelaRF14LeituraActivity::class.java)
            .putExtra("LIVRO_ID", livroId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }
}
