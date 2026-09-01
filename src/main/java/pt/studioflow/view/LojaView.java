package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.*;
import pt.studioflow.repository.*;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Route(value = "loja", layout = MainLayout.class)
@PageTitle("Loja | CoreoFlow")
@RolesAllowed("ADMIN")
public class LojaView extends VerticalLayout {

    private final ProdutoLojaRepository produtoRepo;
    private final EncomendaLojaRepository encomendaRepo;
    private final AlunoRepository alunoRepo;

    public LojaView(ProdutoLojaRepository produtoRepo,
                    EncomendaLojaRepository encomendaRepo,
                    AlunoRepository alunoRepo) {
        this.produtoRepo = produtoRepo;
        this.encomendaRepo = encomendaRepo;
        this.alunoRepo = alunoRepo;

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        H2 titulo = new H2("Loja");
        titulo.getStyle().set("margin", "0 0 8px 0").set("padding", "20px 20px 0 20px");

        TabSheet tabs = new TabSheet();
        tabs.setSizeFull();
        tabs.add("🛍 Produtos", criarTabProdutos());
        tabs.add("📦 Encomendas", criarTabEncomendas());
        add(titulo, tabs);
    }

    // ===================== PRODUTOS =====================

    private VerticalLayout criarTabProdutos() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);

        Grid<ProdutoLoja> grid = new Grid<>(ProdutoLoja.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();

        grid.addColumn(ProdutoLoja::getNome).setHeader("Nome").setFlexGrow(2).setSortable(true);
        grid.addColumn(ProdutoLoja::getCategoria).setHeader("Categoria").setAutoWidth(true);
        grid.addColumn(p -> String.format("%.2f €", p.getPreco())).setHeader("Preço").setAutoWidth(true);
        grid.addComponentColumn(p -> {
            int s = p.getStock();
            Span b = new Span(s + " un.");
            b.getStyle().set("color", s == 0 ? "#E74C3C" : s < 5 ? "#E67E22" : "#27AE60")
                    .set("font-weight", "700");
            return b;
        }).setHeader("Stock").setAutoWidth(true);
        grid.addComponentColumn(p -> {
            Span b = new Span(p.isAtivo() ? "Ativo" : "Inativo");
            b.getStyle().set("color", p.isAtivo() ? "#27AE60" : "#E74C3C").set("font-weight", "600");
            return b;
        }).setHeader("Estado").setAutoWidth(true);
        grid.addComponentColumn(p -> {
            Button editar = new Button(VaadinIcon.EDIT.create(), e -> abrirDialogProduto(p, grid));
            editar.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            Button del = new Button(VaadinIcon.TRASH.create(), e -> { produtoRepo.delete(p); atualizarProdutos(grid); });
            del.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return new HorizontalLayout(editar, del);
        }).setHeader("Ações").setAutoWidth(true);

        atualizarProdutos(grid);
        layout.add(ViewUtils.toolbar(ViewUtils.botaoNovo("Novo Produto", e -> abrirDialogProduto(null, grid))), grid);
        layout.expand(grid);
        return layout;
    }

    private void atualizarProdutos(Grid<ProdutoLoja> grid) {
        Studio s = TenantContext.getCurrentStudio();
        grid.setItems(s != null ? produtoRepo.findByStudioOrderByNomeAsc(s) : produtoRepo.findAll());
    }

    private void abrirDialogProduto(ProdutoLoja p, Grid<ProdutoLoja> grid) {
        boolean novo = p == null;
        ProdutoLoja produto = novo ? new ProdutoLoja() : p;
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(novo ? "Novo Produto" : "Editar " + produto.getNome());
        dialog.setWidth("440px");

        TextField nome = new TextField("Nome");
        nome.setWidthFull();
        nome.setValue(produto.getNome() != null ? produto.getNome() : "");

        TextField categoria = new TextField("Categoria");
        categoria.setPlaceholder("ex: Vestuário, Equipamento");
        categoria.setWidthFull();
        categoria.setValue(produto.getCategoria() != null ? produto.getCategoria() : "");

        NumberField preco = new NumberField("Preço (€)");
        preco.setMin(0); preco.setWidthFull();
        preco.setValue(produto.getPreco());

        IntegerField stock = new IntegerField("Stock");
        stock.setMin(0); stock.setWidthFull();
        stock.setValue(produto.getStock());

        TextArea desc = new TextArea("Descrição");
        desc.setWidthFull();
        desc.setValue(produto.getDescricao() != null ? produto.getDescricao() : "");

        Button guardar = new Button("Guardar", e -> {
            if (nome.isEmpty()) { Notification.show("Nome obrigatório"); return; }
            produto.setNome(nome.getValue().trim());
            produto.setCategoria(categoria.getValue().trim());
            produto.setPreco(preco.getValue() != null ? preco.getValue() : 0);
            produto.setStock(stock.getValue() != null ? stock.getValue() : 0);
            produto.setDescricao(desc.getValue().trim());
            produto.setStudio(TenantContext.getCurrentStudio());
            produtoRepo.save(produto);
            atualizarProdutos(grid);
            dialog.close();
            Notification.show("Produto guardado").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(new FormLayout(nome, categoria, preco, stock, desc));
        dialog.getFooter().add(new Button("Cancelar", e -> dialog.close()), guardar);
        dialog.open();
    }

    // ===================== ENCOMENDAS =====================

    private VerticalLayout criarTabEncomendas() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);

        Grid<EncomendaLoja> grid = new Grid<>(EncomendaLoja.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();

        grid.addColumn(e -> e.getDataEncomenda().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
                .setHeader("Data").setAutoWidth(true).setSortable(true);
        grid.addColumn(e -> e.getAluno().getNomeCompleto()).setHeader("Aluno").setFlexGrow(1);
        grid.addColumn(e -> e.getProduto().getNome()).setHeader("Produto").setFlexGrow(1);
        grid.addColumn(e -> e.getQuantidade() + " un.").setHeader("Qtd").setAutoWidth(true);
        grid.addColumn(e -> String.format("%.2f €", e.getValorTotal())).setHeader("Total").setAutoWidth(true);
        grid.addComponentColumn(e -> {
            String[] cfg = switch (e.getEstado()) {
                case PENDENTE   -> new String[]{"#fff3e0","#E67E22","Pendente"};
                case CONFIRMADA -> new String[]{"#e3f2fd","#1976D2","Confirmada"};
                case ENTREGUE   -> new String[]{"#e8f5e9","#27AE60","Entregue"};
                case CANCELADA  -> new String[]{"#fce4ec","#C62828","Cancelada"};
            };
            Span b = new Span(cfg[2]);
            b.getStyle().set("background",cfg[0]).set("color",cfg[1])
                    .set("padding","2px 8px").set("border-radius","10px")
                    .set("font-size","11px").set("font-weight","600");
            return b;
        }).setHeader("Estado").setAutoWidth(true);
        grid.addComponentColumn(e -> {
            ComboBox<EncomendaLoja.Estado> estado = new ComboBox<>();
            estado.setItems(EncomendaLoja.Estado.values());
            estado.setValue(e.getEstado());
            estado.setWidth("140px");
            estado.addValueChangeListener(ev -> {
                e.setEstado(ev.getValue());
                encomendaRepo.save(e);
                atualizarEncomendas(grid);
            });
            return estado;
        }).setHeader("Atualizar").setAutoWidth(true);

        atualizarEncomendas(grid);
        layout.add(ViewUtils.toolbar(ViewUtils.botaoNovo("Nova Encomenda", e -> abrirDialogEncomenda(grid))), grid);
        layout.expand(grid);
        return layout;
    }

    private void atualizarEncomendas(Grid<EncomendaLoja> grid) {
        Studio s = TenantContext.getCurrentStudio();
        grid.setItems(s != null ? encomendaRepo.findByStudioOrderByDataEncomendaDesc(s)
                                 : encomendaRepo.findAll());
    }

    private void abrirDialogEncomenda(Grid<EncomendaLoja> grid) {
        Studio studio = TenantContext.getCurrentStudio();
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Nova Encomenda");
        dialog.setWidth("440px");

        ComboBox<Aluno> aluno = new ComboBox<>("Aluno");
        aluno.setItems(studio != null ? alunoRepo.findAllByStudio(studio) : alunoRepo.findAll());
        aluno.setItemLabelGenerator(Aluno::getNomeCompleto);
        aluno.setRequired(true); aluno.setWidthFull();

        ComboBox<ProdutoLoja> produto = new ComboBox<>("Produto");
        produto.setItems(studio != null ? produtoRepo.findByStudioAndAtivoTrueOrderByNomeAsc(studio)
                                        : produtoRepo.findAll());
        produto.setItemLabelGenerator(p -> p.getNome() + " (" + p.getStock() + " un.) — "
                + String.format("%.2f €", p.getPreco()));
        produto.setRequired(true); produto.setWidthFull();

        IntegerField qtd = new IntegerField("Quantidade");
        qtd.setMin(1); qtd.setValue(1); qtd.setWidthFull();

        Button guardar = new Button("Registar", e -> {
            if (aluno.getValue() == null || produto.getValue() == null) {
                Notification.show("Aluno e Produto são obrigatórios"); return;
            }
            ProdutoLoja p = produto.getValue();
            int q = qtd.getValue() != null ? qtd.getValue() : 1;
            if (p.getStock() < q) {
                Notification.show("Stock insuficiente! Disponível: " + p.getStock()); return;
            }
            EncomendaLoja enc = new EncomendaLoja();
            enc.setAluno(aluno.getValue());
            enc.setProduto(p);
            enc.setQuantidade(q);
            enc.setValorTotal(p.getPreco() * q);
            enc.setStudio(studio);
            encomendaRepo.save(enc);
            // Atualizar stock
            p.setStock(p.getStock() - q);
            produtoRepo.save(p);
            atualizarEncomendas(grid);
            dialog.close();
            Notification.show("Encomenda registada").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(new FormLayout(aluno, produto, qtd));
        dialog.getFooter().add(new Button("Cancelar", e -> dialog.close()), guardar);
        dialog.open();
    }
}
