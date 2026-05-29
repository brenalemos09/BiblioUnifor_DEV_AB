package com.example.bibliounifornew.features.usuario.biblioteca

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.example.bibliounifornew.R
import com.example.bibliounifornew.data.AuthRepository
import com.example.bibliounifornew.features.usuario.livro.TelaRF11TelaDePesquisa
import com.example.bibliounifornew.features.usuario.solicitacao.TelaRF19SolicitacoesTermosCondicoes
import com.google.firebase.firestore.FirebaseFirestore

class TelaRF14LeituraActivity : AppCompatActivity() {

    private val authRepository = AuthRepository()
    private val db             = FirebaseFirestore.getInstance()

    private var livroIdAtual   : String = ""
    private var linkPdfAtual   : String = ""
    private var linkAudioAtual : String = ""
    private var tituloAtual    : String = ""
    private var autorAtual     : String = ""
    private var coverUrlAtual  : String = ""
    private var setorAtual     : String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.telarf14_leitura)

        livroIdAtual = intent.getStringExtra("LIVRO_ID") ?: ""

        // Placeholders de carregamento
        findViewById<TextView>(R.id.textTituloLivroAcoes)?.text    = getString(R.string.carregando_dados)
        findViewById<TextView>(R.id.textAutorLivroAcoes)?.text     = ""
        findViewById<TextView>(R.id.textCategoriaLivroAcoes)?.text = ""

        // PDF e Audiobook começam desabilitados — só habilitados após validação da URL
        desabilitarBotaoMidia(R.id.buttonAbrirPdfLivro,   R.id.textStatusPdf)
        desabilitarBotaoMidia(R.id.buttonAbrirAudioLivro, R.id.textStatusAudio)

        if (livroIdAtual.isNotEmpty()) {
            carregarCabecalho(livroIdAtual)
            verificarStatusAluguelRapido()
        }

        // ─── ALUGAR → Termos e Condições (RF19) ──────────────────────────────
        // Mesmo fluxo dos outros botões: todos passam pela tela de Termos primeiro.
        findViewById<Button>(R.id.buttonAlugarLivro)?.setOnClickListener {
            irParaTermos("Aluguel")
        }

        // ─── RESERVAR → Termos e Condições ───────────────────────────────────
        findViewById<Button>(R.id.buttonReservar)?.setOnClickListener {
            irParaTermos("Reserva")
        }

        // ─── SOLICITAÇÕES DE MÍDIA → Termos e Condições ──────────────────────
        findViewById<Button>(R.id.buttonSolicitarPdf)?.setOnClickListener {
            irParaTermos("PDF")
        }
        findViewById<Button>(R.id.buttonSolicitarBraille)?.setOnClickListener {
            irParaTermos("Braille")
        }
        findViewById<Button>(R.id.buttonSolicitarAudio)?.setOnClickListener {
            irParaTermos("Audiobook")
        }

        // ─── ABRIR PDF ────────────────────────────────────────────────────────
        // Habilitado/desabilitado dinamicamente por atualizarIndicadoresMidia()
        findViewById<Button>(R.id.buttonAbrirPdfLivro)?.setOnClickListener {
            abrirUrlExterna(linkPdfAtual)
        }

        // ─── ABRIR AUDIOBOOK ──────────────────────────────────────────────────
        findViewById<Button>(R.id.buttonAbrirAudioLivro)?.setOnClickListener {
            abrirUrlExterna(linkAudioAtual)
        }

        // ─── SETOR LOCALIZADO ─────────────────────────────────────────────────
        findViewById<Button>(R.id.buttonSetorLivro)?.setOnClickListener {
            showPopupSetor()
        }

        // ─── PROCURAR ─────────────────────────────────────────────────────────
        // Usa CLEAR_TOP para reaproveitar instância existente na pilha;
        // finish() garante que esta Activity não fique acumulada acima da busca.
        findViewById<Button>(R.id.buttonProcurarLivro)?.setOnClickListener {
            startActivity(
                Intent(this, TelaRF11TelaDePesquisa::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
        }
    }

    // ─── CABEÇALHO DINÂMICO ───────────────────────────────────────────────────

    private fun carregarCabecalho(id: String) {
        db.collection("livros").document(id).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists() || isFinishing || isDestroyed) return@addOnSuccessListener

                tituloAtual    = doc.getString("title")         ?: doc.getString("titulo")    ?: "Título Indisponível"
                autorAtual     = doc.getString("author")        ?: doc.getString("autor")     ?: "Autor Desconhecido"
                val categoria  = doc.getString("category")      ?: doc.getString("categoria") ?: "Geral"
                val coverUrl   = doc.getString("coverUrl")      ?: ""
                coverUrlAtual  = coverUrl
                setorAtual     = doc.getString("librarySector") ?: doc.getString("setor")     ?: getString(R.string.msg_setor_nao_informado)
                linkPdfAtual   = doc.getString("linkPdf")       ?: ""
                linkAudioAtual = doc.getString("linkAudiobook") ?: ""

                findViewById<TextView>(R.id.textTituloLivroAcoes)?.text    = tituloAtual
                findViewById<TextView>(R.id.textAutorLivroAcoes)?.text     = autorAtual
                findViewById<TextView>(R.id.textCategoriaLivroAcoes)?.text = categoria

                val imgCapa = findViewById<ImageView>(R.id.imageLivroAcoes)
                if (coverUrl.isNotEmpty()) {
                    imgCapa?.load(coverUrl) {
                        placeholder(R.drawable.osda)
                        error(R.drawable.osda)
                        crossfade(true)
                        size(500, 750)
                    }
                } else {
                    imgCapa?.setImageResource(R.drawable.osda)
                }

                // Após preencher as URLs, atualiza os indicadores de disponibilidade
                atualizarIndicadoresMidia()
            }
            .addOnFailureListener {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, getString(R.string.erro_carregar_dados_livro), Toast.LENGTH_SHORT).show()
                    findViewById<TextView>(R.id.textTituloLivroAcoes)?.text = getString(R.string.sem_titulo)
                }
            }
    }

    // ─── INDICADORES DE DISPONIBILIDADE DE MÍDIA ──────────────────────────────

    private fun desabilitarBotaoMidia(btnId: Int, tvId: Int) {
        findViewById<Button>(btnId)?.apply {
            isEnabled = false
            alpha     = 0.5f
        }
        // TextView fica sem texto enquanto aguarda carregamento
        findViewById<TextView>(tvId)?.text = ""
    }

    private fun atualizarIndicadoresMidia() {
        val corDisponivel   = Color.parseColor("#2E7D32")
        val corIndisponivel = Color.parseColor("#C62828")

        // PDF
        val btnPdf       = findViewById<Button>(R.id.buttonAbrirPdfLivro)
        val txtStatusPdf = findViewById<TextView>(R.id.textStatusPdf)
        if (linkPdfAtual.isNotBlank()) {
            txtStatusPdf?.text = getString(R.string.msg_midia_disponivel)
            txtStatusPdf?.setTextColor(corDisponivel)
            btnPdf?.isEnabled = true
            btnPdf?.alpha     = 1.0f
        } else {
            txtStatusPdf?.text = getString(R.string.msg_midia_indisponivel_servidor)
            txtStatusPdf?.setTextColor(corIndisponivel)
            btnPdf?.isEnabled = false
            btnPdf?.alpha     = 0.5f
        }

        // Audiobook
        val btnAudio       = findViewById<Button>(R.id.buttonAbrirAudioLivro)
        val txtStatusAudio = findViewById<TextView>(R.id.textStatusAudio)
        if (linkAudioAtual.isNotBlank()) {
            txtStatusAudio?.text = getString(R.string.msg_midia_disponivel)
            txtStatusAudio?.setTextColor(corDisponivel)
            btnAudio?.isEnabled = true
            btnAudio?.alpha     = 1.0f
        } else {
            txtStatusAudio?.text = getString(R.string.msg_midia_indisponivel_servidor)
            txtStatusAudio?.setTextColor(corIndisponivel)
            btnAudio?.isEnabled = false
            btnAudio?.alpha     = 0.5f
        }
    }

    // ─── NAVEGAÇÃO PARA TERMOS (RF19) ─────────────────────────────────────────

    private fun irParaTermos(tipo: String) {
        if (tituloAtual.isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_aguarde_carregamento), Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(this, TelaRF19SolicitacoesTermosCondicoes::class.java).apply {
                putExtra("TIPO_MIDIA", tipo)
                putExtra("LIVRO_ID",   livroIdAtual)
                putExtra("TITULO",     tituloAtual)
                putExtra("AUTOR",      autorAtual)
            }
        )
    }

    // ─── VERIFICAÇÃO DE ALUGUEL EXISTENTE ────────────────────────────────────

    private fun verificarStatusAluguelRapido() {
        val uid = authRepository.getUsuarioAtual()?.uid ?: return
        db.collection("solicitacoes_emprestimo")
            .whereEqualTo("uidAluno", uid)
            .whereEqualTo("idLivro", livroIdAtual)
            .get()
            .addOnSuccessListener { snapshot ->
                val jaAlugado = snapshot.documents.any { doc ->
                    doc.getString("status") in listOf("pendente", "ativo", "atrasado")
                }
                if (jaAlugado) {
                    val btnAlugar = findViewById<Button>(R.id.buttonAlugarLivro)
                    btnAlugar?.text      = getString(R.string.label_alugado)
                    btnAlugar?.isEnabled = false
                    btnAlugar?.alpha     = 0.6f
                }
            }
    }

    // ─── ABRIR URL EXTERNA ────────────────────────────────────────────────────

    private fun abrirUrlExterna(url: String) {
        if (url.isBlank()) return
        try {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                    null
                )
            )
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.erro_abrir_link), Toast.LENGTH_SHORT).show()
        }
    }

    // ─── POPUP SETOR LOCALIZADO ───────────────────────────────────────────────

    private fun showPopupSetor() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.popup_setor_localizado)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialog.findViewById<TextView>(R.id.textLivroSetor)?.text =
            "Livro: $tituloAtual"
        dialog.findViewById<TextView>(R.id.textSetorLocalizado)?.text =
            setorAtual.ifBlank { getString(R.string.msg_setor_nao_informado) }

        dialog.findViewById<Button>(R.id.buttonVoltarSetor)?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
