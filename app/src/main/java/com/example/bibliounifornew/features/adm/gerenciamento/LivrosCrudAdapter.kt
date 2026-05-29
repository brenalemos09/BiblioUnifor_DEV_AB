package com.example.bibliounifornew.features.adm.gerenciamento

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.bibliounifornew.R
import com.google.android.material.button.MaterialButton

// ─── Modelo de dado ───────────────────────────────────────────────────────────
data class ItemLivroAdm(
    val docId               : String = "",
    val titulo              : String = "Título Indisponível",
    val autor               : String = "Autor Desconhecido",
    val isbn                : String = "",
    val quantidadeDisponivel: Long   = 0L,  // cópias disponíveis em tempo real
    val totalExemplares     : Long   = 0L,  // total físico da faculdade
    val coverUrl            : String = ""
)

// ─── Adapter ─────────────────────────────────────────────────────────────────
class LivrosCrudAdapter(
    private val lista    : MutableList<ItemLivroAdm>,
    private val onEditar : (ItemLivroAdm) -> Unit
) : RecyclerView.Adapter<LivrosCrudAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgCapa  : ImageView      = itemView.findViewById(R.id.imgCapaLivroCrud)
        val txtTitulo: TextView       = itemView.findViewById(R.id.txtTituloLivroCrud)
        val txtAutor : TextView       = itemView.findViewById(R.id.txtAutorLivroCrud)
        val txtIsbn  : TextView       = itemView.findViewById(R.id.txtIsbnLivroCrud)
        val txtQtd   : TextView       = itemView.findViewById(R.id.txtQuantidadeLivroCrud)
        val btnEditar: MaterialButton = itemView.findViewById(R.id.btnEditarLivroCrud)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_livro_crud_adm, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lista[position]
        val ctx  = holder.itemView.context

        holder.txtTitulo.text = item.titulo
        holder.txtAutor.text  = item.autor

        holder.txtIsbn.text = if (item.isbn.isNotEmpty())
            ctx.getString(R.string.fmt_isbn, item.isbn)
        else
            ctx.getString(R.string.fmt_isbn_vazio)

        // Exibe "disp/total disponíveis" seguindo a regra de negócio unificada
        val disp  = item.quantidadeDisponivel
        val total = item.totalExemplares
        holder.txtQtd.text = when {
            total > 0 -> ctx.getString(R.string.fmt_exemplares_ratio, disp.toInt(), total.toInt())
            else      -> ctx.getString(R.string.fmt_exemplares_vazio)
        }

        // Fallback neutro (ic_sem_capa) — evita exibir capa de outro livro
        holder.imgCapa.load(item.coverUrl.ifEmpty { null }) {
            allowHardware(false)
            placeholder(R.drawable.ic_sem_capa)
            error(R.drawable.ic_sem_capa)
            fallback(R.drawable.ic_sem_capa)
        }

        holder.btnEditar.setOnClickListener { onEditar(item) }
    }

    override fun getItemCount(): Int = lista.size

    fun atualizarLista(novaLista: List<ItemLivroAdm>) {
        lista.clear()
        lista.addAll(novaLista)
        notifyDataSetChanged()
    }
}
