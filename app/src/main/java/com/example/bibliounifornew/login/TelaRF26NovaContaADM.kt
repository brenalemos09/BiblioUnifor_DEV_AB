package com.example.bibliounifornew.login

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.bibliounifornew.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class TelaRF26NovaContaADM : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.telarf26_nova_conta_adm
        )



        //--------------------------------
        // CAMPOS
        //--------------------------------

        val nome =

            findViewById<EditText>(
                R.id.editNomeCompletoAdm
            )


        val usuario =

            findViewById<EditText>(
                R.id.editNomeUsuarioAdm
            )


        val email =

            findViewById<EditText>(
                R.id.editEmailAdmCadastro
            )


        val credencial =

            findViewById<EditText>(
                R.id.editCredencialAdmCadastro
            )


        val senha =

            findViewById<EditText>(
                R.id.editSenhaAdmCadastro
            )


        val confirma =

            findViewById<EditText>(
                R.id.editConfirmarSenhaAdm
            )



        //--------------------------------
        // ERROS
        //--------------------------------

        val erroEmail =
            findViewById<TextView>(
                R.id.textErroEmailAdmCadastro
            )


        val erroCredencial =
            findViewById<TextView>(
                R.id.textErroCredencialAdm
            )


        val erroSenha =
            findViewById<TextView>(
                R.id.textRegrasSenhaAdm
            )

        val erroSenha1 =
            findViewById<TextView>(
                R.id.textErroSenha1
            )

        val erroSenha2 =
            findViewById<TextView>(
                R.id.textErroSenha2
            )

        val erroSenhaDiferente =
            findViewById<TextView>(
                R.id.textErroSenhaDiferente
            )

        val erroSenhaIgual =
            findViewById<TextView>(
                R.id.textErroSenhaIgual
            )



        //--------------------------------
        // ESTADO INICIAL (OCULTAR ERROS)
        //--------------------------------

        erroEmail.visibility = View.GONE
        erroCredencial.visibility = View.GONE
        erroSenha.visibility = View.GONE
        erroSenha1.visibility = View.GONE
        erroSenha2.visibility = View.GONE
        erroSenhaDiferente.visibility = View.GONE
        erroSenhaIgual.visibility = View.GONE

        //--------------------------------
        // TEXT WATCHER (LIMPAR ERROS)
        //--------------------------------

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                erroEmail.visibility = View.GONE
                erroCredencial.visibility = View.GONE
                erroSenha.visibility = View.GONE
                erroSenha1.visibility = View.GONE
                erroSenha2.visibility = View.GONE
                erroSenhaDiferente.visibility = View.GONE
                erroSenhaIgual.visibility = View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        nome.addTextChangedListener(watcher)
        usuario.addTextChangedListener(watcher)
        email.addTextChangedListener(watcher)
        credencial.addTextChangedListener(watcher)
        senha.addTextChangedListener(watcher)
        confirma.addTextChangedListener(watcher)

        //--------------------------------
        // BOTÃO
        //--------------------------------

        val criar =

            findViewById<Button>(
                R.id.buttonCriarContaAdm
            )


        val entrar =

            findViewById<TextView>(
                R.id.textEntreAquiAdm
            )



        //--------------------------------
        // OLHOS
        //--------------------------------

        val olho1 =
            findViewById<ImageView>(
                R.id.iconOlhoSenhaAdmCadastro
            )

        val olho2 =
            findViewById<ImageView>(
                R.id.iconOlhoConfirmarSenhaAdm
            )



        //--------------------------------
        // MOSTRAR SENHA
        //--------------------------------

        var v1=false
        var v2=false



        olho1.setOnClickListener{
            v1 = !v1
            if(v1){
                senha.transformationMethod = HideReturnsTransformationMethod.getInstance()
                olho1.setImageResource(R.drawable.ic_eye_open)
            } else {
                senha.transformationMethod = PasswordTransformationMethod.getInstance()
                olho1.setImageResource(R.drawable.ic_eye_closed)
            }
            senha.setSelection(senha.text.length)
        }

        olho2.setOnClickListener{
            v2 = !v2
            if(v2){
                confirma.transformationMethod = HideReturnsTransformationMethod.getInstance()
                olho2.setImageResource(R.drawable.ic_eye_open)
            } else {
                confirma.transformationMethod = PasswordTransformationMethod.getInstance()
                olho2.setImageResource(R.drawable.ic_eye_closed)
            }
            confirma.setSelection(confirma.text.length)
        }



        //--------------------------------
        // CRIAR CONTA
        //--------------------------------

        criar.setOnClickListener{

            erroEmail.visibility = View.GONE
            erroCredencial.visibility = View.GONE
            erroSenha.visibility = View.GONE
            erroSenha1.visibility = View.GONE
            erroSenha2.visibility = View.GONE
            erroSenhaDiferente.visibility = View.GONE
            erroSenhaIgual.visibility = View.GONE

            val sNome = nome.text.toString().trim()
            val sUsuario = usuario.text.toString().trim()
            val sEmail = email.text.toString().trim()
            val sCredencial = credencial.text.toString().trim()
            val sSenha = senha.text.toString()
            val sConfirma = confirma.text.toString()

            // ── Validações síncronas (sem I/O) ───────────────────────────────
            when {
                sNome.isEmpty() || sUsuario.isEmpty() || sEmail.isEmpty() || sCredencial.isEmpty() -> {
                    Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                !android.util.Patterns.EMAIL_ADDRESS.matcher(sEmail).matches() -> {
                    erroEmail.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                sSenha.isEmpty() -> {
                    erroSenha1.text = "Campo obrigatório"
                    erroSenha1.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                sConfirma.isEmpty() -> {
                    erroSenha2.text = "Campo obrigatório"
                    erroSenha2.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                sSenha.length < 8 -> {
                    erroSenha.text = "A senha deve conter pelo menos 8 caracteres"
                    erroSenha.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                !sSenha.any { it.isDigit() } -> {
                    erroSenha.text = "Um número"
                    erroSenha.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                !sSenha.any { it.isUpperCase() } -> {
                    erroSenha.text = "Uma letra maiúscula"
                    erroSenha.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                sSenha != sConfirma -> {
                    erroSenhaDiferente.visibility = View.VISIBLE
                    return@setOnClickListener
                }
            }

            // ── Validação da credencial de equipe ─────────────────────────────
            // BUG-CRED FIX: chegou a ser validada lendo configuracoes/adm no
            // Firestore (código rotacionável sem rebuild), mas mesmo autenticado
            // a leitura falhava com PERMISSION_DENIED (confirmado via Logcat) —
            // as regras de segurança do Firestore bloqueiam essa coleção mesmo
            // para um usuário recém-criado, o que travava 100% dos cadastros ADM.
            // Revertido para a mesma checagem local usada em TelaRF23LoginADM,
            // sem depender de uma leitura ao Firestore que nunca teve permissão
            // configurada para funcionar.
            if (sCredencial != "DevsAB") {
                erroCredencial.visibility = View.VISIBLE
                return@setOnClickListener
            }

            criar.isEnabled = false
            criar.text = "Criando..."

            auth.createUserWithEmailAndPassword(sEmail, sSenha)
                .addOnCompleteListener { authTask ->
                    if (!authTask.isSuccessful) {
                        criar.isEnabled = true
                        criar.text = "Criar Conta ADM"
                        Toast.makeText(this, traduzirErroFirebase(authTask.exception?.message), Toast.LENGTH_LONG).show()
                        return@addOnCompleteListener
                    }

                    val user = authTask.result?.user
                    val uid  = user?.uid
                    if (user == null || uid == null) {
                        criar.isEnabled = true
                        criar.text = "Criar Conta ADM"
                        Toast.makeText(this, "Ocorreu um erro inesperado. Tente novamente.", Toast.LENGTH_LONG).show()
                        return@addOnCompleteListener
                    }

                    val agora = System.currentTimeMillis()
                    val perfil = hashMapOf(
                        "uid"                to uid,
                        "nome"               to sNome,
                        "usuario"            to sUsuario,
                        "email"              to sEmail,
                        "role"               to "adm",
                        "cadastroConfirmado" to true,  // ADM não precisa de aprovação
                        "contaAtiva"         to true,
                        "criadoEm"           to agora
                    )

                    // RF36 FIX CRÍTICO: WriteBatch grava em AMBAS as coleções.
                    // TelaRF23LoginADM verifica role em "usuarios" — sem esse doc,
                    // o ADM recebe "Perfil não encontrado" ao tentar logar.
                    // "administradores" é mantido para o dashboard carregar o perfil.
                    val batch = db.batch()
                    batch.set(
                        db.collection("usuarios").document(uid),
                        perfil,
                        SetOptions.merge()
                    )
                    batch.set(
                        db.collection("administradores").document(uid),
                        perfil,
                        SetOptions.merge()
                    )

                    batch.commit()
                        .addOnSuccessListener {
                            user.sendEmailVerification()
                            auth.signOut()
                            popup()
                        }
                        .addOnFailureListener {
                            auth.signOut()
                            criar.isEnabled = true
                            criar.text = "Criar Conta ADM"
                            Toast.makeText(this, "Conta criada, mas não foi possível salvar o perfil. Tente fazer login.", Toast.LENGTH_LONG).show()
                            popup()
                        }
                }
        }



        //--------------------------------
        // ENTRE AQUI
        //--------------------------------

        entrar.setOnClickListener{

            startActivity(

                Intent(

                    this,

                    TelaRF23LoginADM::class.java

                )

            )

            finish()

        }

    }




    //--------------------------------
    // POPUP
    //--------------------------------

    private fun popup(){

        val dialog=
            Dialog(this)

        dialog.setContentView(
            R.layout.popup_sucesso_cadastro
        )

        dialog.setCancelable(false)

        dialog.window
            ?.setBackgroundDrawableResource(
                android.R.color.transparent
            )



        val voltar=

            dialog.findViewById<Button>(
                R.id.btnRetornarLogin
            )



        voltar.setOnClickListener {

            startActivity(

                Intent(

                    this,

                    TelaRF23LoginADM::class.java

                )

            )

            dialog.dismiss()

            finish()

        }



        dialog.show()

    }

    //--------------------------------
    // TRADUÇÃO DE ERROS DO FIREBASE
    //--------------------------------

    private fun traduzirErroFirebase(mensagem: String?): String {
        return when {
            mensagem == null                                   -> "Ocorreu um erro inesperado. Tente novamente."
            mensagem.contains("email address is already")     -> "Este e-mail já está cadastrado."
            mensagem.contains("badly formatted")              -> "Formato de e-mail inválido."
            mensagem.contains("network error", ignoreCase = true)
                || mensagem.contains("unreachable")           -> "Sem conexão com a internet. Verifique sua rede."
            mensagem.contains("password is invalid")
                || mensagem.contains("least 6 characters")   -> "Senha muito fraca. Use pelo menos 8 caracteres."
            mensagem.contains("too many requests", ignoreCase = true) -> "Muitas tentativas. Aguarde alguns minutos e tente novamente."
            else                                              -> "Não foi possível criar a conta. Tente novamente."
        }
    }

}