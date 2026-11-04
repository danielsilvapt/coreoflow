package pt.studioflow.view;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.security.RolesAllowed;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.MetodoRegistoPresenca;
import pt.studioflow.model.Presenca;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.PresencaRepository;
import pt.studioflow.service.AuthService;
import pt.studioflow.service.CheckinService;
import pt.studioflow.service.CheckinService.AulaHojeKiosk;
import pt.studioflow.service.R2StorageService;
import pt.studioflow.service.TurmaService;
import pt.studioflow.util.LogoUrl;

/**
 * Kiosk de checkin — página standalone (sem MainLayout/drawer) pensada para o
 * perfil PRESENÇA: uma pessoa ou um iPad fixo na sala que só sabe marcar
 * presenças.
 *
 * <p>Ecrã inicial: identifica a CoreoFlow e o estúdio, dá as boas-vindas, mostra
 * as aulas "agora" (checkin aberto / a decorrer) e as próximas aulas de hoje com
 * contagem decrescente até o checkin abrir. Atualiza-se sozinho por poll; ao
 * escolher uma turma mostra o roster (matriculados + créditos de aula avulsa
 * válidos) para tocar e marcar presente. Com o roster aberto o poll só atualiza
 * o relógio e o estado da janela — nunca re-renderiza a lista de alunos.
 */
@Route("checkin")
@PageTitle("Checkin")
@RolesAllowed({ "PRESENCA", "ADMIN", "PROF" })
public class CheckinKioskView extends VerticalLayout {

    private static final int POLL_MS = 15_000;
    private static final Locale PT = Locale.forLanguageTag("pt-PT");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATA_LONGA = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT);

    private final AuthService authService;
    private final CheckinService checkinService;
    private final TurmaService turmaService;
    private final PresencaRepository presencaRepository;
    private final R2StorageService storageService;

    private final Div conteudo = new Div();
    private final Span relogioHora = new Span();
    private final Span relogioData = new Span();

    /** Turma com roster aberto (null = ecrã de lista). */
    private Turma turmaAberta;
    private AulaHojeKiosk aulaAberta;
    /** Estado da janela no cabeçalho do roster, atualizado pelo poll sem tocar no roster. */
    private Span rosterEstado;
    /** Assinatura do último ecrã de lista desenhado: só se redesenha quando o conteúdo muda. */
    private String chaveListaAtual;
    private Registration pollRegistration;

    public CheckinKioskView(AuthService authService, CheckinService checkinService, TurmaService turmaService,
            PresencaRepository presencaRepository, R2StorageService storageService) {
        this.authService = authService;
        this.checkinService = checkinService;
        this.turmaService = turmaService;
        this.presencaRepository = presencaRepository;
        this.storageService = storageService;

        authService.initTenantContext();

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("cf-kiosk");
        // Sem MainLayout: a cor do estúdio (--cf-brand) é definida aqui, no próprio kiosk.
        Studio studio = studioAtual();
        if (studio != null && studio.getCorPrimaria() != null
                && studio.getCorPrimaria().matches("#[0-9a-fA-F]{3,8}")) {
            getStyle().set("--cf-brand", studio.getCorPrimaria());
        }

        conteudo.addClassName("cf-kiosk-main");
        add(criarCabecalho(), conteudo);

        atualizarRelogio(LocalDateTime.now());
        mostrarListaTurmas();
    }

    // ------------------------------------------------------------------
    // Ciclo de vida / atualização automática
    // ------------------------------------------------------------------

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        UI ui = attachEvent.getUI();
        ui.setPollInterval(POLL_MS);
        pollRegistration = ui.addPollListener(e -> aoAtualizar());
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (pollRegistration != null) {
            pollRegistration.remove();
            pollRegistration = null;
        }
        detachEvent.getUI().setPollInterval(-1);
        super.onDetach(detachEvent);
    }

    private void aoAtualizar() {
        LocalDateTime agora = LocalDateTime.now();
        atualizarRelogio(agora);
        if (turmaAberta == null) {
            mostrarListaTurmas();
        } else if (rosterEstado != null && aulaAberta != null) {
            // Roster aberto: só o texto de estado muda, a lista de alunos fica intacta.
            rosterEstado.setText(textoEstadoAgora(aulaAberta, agora));
            rosterEstado.setClassName("cf-kiosk-roster-status--closed", !aulaAberta.checkinAberto(agora));
        }
    }

    private Studio studioAtual() {
        return authService.getCurrentStudio();
    }

    // ------------------------------------------------------------------
    // Cabeçalho: marca CoreoFlow + estúdio + relógio
    // ------------------------------------------------------------------

    private Component criarCabecalho() {
        Div header = new Div();
        header.addClassName("cf-kiosk-header");

        Image appLogo = new Image("/images/logo2-coreoflow.png", "CoreoFlow");
        appLogo.addClassName("cf-kiosk-app-logo");

        Div estudio = new Div();
        estudio.addClassName("cf-kiosk-studio");
        Studio studio = studioAtual();
        if (studio != null) {
            String nome = studio.getNome() != null ? studio.getNome() : "Estúdio";
            if (studio.getLogoPath() != null && !studio.getLogoPath().isBlank()) {
                Image logo = new Image(LogoUrl.comVersao(studio.getLogoPath()), nome);
                logo.addClassName("cf-kiosk-studio-logo");
                estudio.add(logo);
            }
            Span nomeSpan = new Span(nome);
            nomeSpan.addClassName("cf-kiosk-studio-name");
            estudio.add(nomeSpan);
        }

        Div relogio = new Div(relogioHora, relogioData);
        relogio.addClassName("cf-kiosk-clock");
        relogioHora.addClassName("cf-kiosk-clock-time");
        relogioData.addClassName("cf-kiosk-clock-date");

        header.add(appLogo, estudio, relogio);
        return header;
    }

    private void atualizarRelogio(LocalDateTime agora) {
        relogioHora.setText(agora.format(HORA));
        relogioData.setText(capitalizar(agora.format(DATA_LONGA)));
    }

    // ------------------------------------------------------------------
    // Ecrã de lista: boas-vindas, agora, a seguir
    // ------------------------------------------------------------------

    private void mostrarListaTurmas() {
        turmaAberta = null;
        aulaAberta = null;
        rosterEstado = null;

        Studio studio = studioAtual();
        if (studio == null) {
            conteudo.removeAll();
            chaveListaAtual = null;
            conteudo.add(estadoVazio(VaadinIcon.WARNING, "Sem estúdio associado a esta conta.", null));
            return;
        }

        LocalDateTime agora = LocalDateTime.now();
        List<AulaHojeKiosk> aulas = checkinService.listarAulasDeHoje(studio, agora);

        List<AulaHojeKiosk> agoraLista = new ArrayList<>();
        List<AulaHojeKiosk> seguir = new ArrayList<>();
        for (AulaHojeKiosk a : aulas) {
            if (a.checkinAberto(agora) || a.aDecorrer(agora)) {
                agoraLista.add(a);
            } else if (agora.isBefore(a.checkinAbre)) {
                seguir.add(a);
            }
        }
        // Checkin aberto primeiro; aulas a decorrer já sem checkin no fim.
        agoraLista.sort(Comparator.comparing((AulaHojeKiosk a) -> !a.checkinAberto(agora))
                .thenComparing(a -> a.inicio));

        // Só redesenha quando algo visível muda (evita "piscar" e toques perdidos a cada poll).
        String chave = saudacao(agora) + "|" + agora.toLocalDate() + "|"
                + agoraLista.stream().map(a -> idAula(a) + ":" + a.checkinAberto(agora) + ":" + textoEstadoAgora(a, agora))
                        .collect(Collectors.joining(","))
                + "|" + seguir.stream().map(a -> idAula(a) + ":" + textoContagem(agora, a.checkinAbre))
                        .collect(Collectors.joining(","));
        if (chave.equals(chaveListaAtual)) {
            return;
        }
        chaveListaAtual = chave;
        conteudo.removeAll();

        // Boas-vindas
        Div hero = new Div();
        hero.addClassName("cf-kiosk-hero");
        // Nome do estúdio fica no cabeçalho — evita "ao/à" com nomes de género variável.
        H1 ola = new H1(saudacao(agora) + "! Bem-vindo(a).");
        ola.addClassName("cf-kiosk-hero-title");
        Paragraph instrucao = new Paragraph("Escolhe a tua aula para fazer checkin.");
        instrucao.addClassName("cf-kiosk-hero-subtitle");
        hero.add(ola, instrucao);
        conteudo.add(hero);

        // Agora
        Div secAgora = seccao("Agora", agoraLista.isEmpty() ? null : String.valueOf(agoraLista.size()), true);
        if (agoraLista.isEmpty()) {
            String detalhe = seguir.isEmpty() ? "Não há mais aulas hoje."
                    : "A próxima é " + seguir.get(0).turma.getDescricao() + " às " + seguir.get(0).inicio.format(HORA) + ".";
            secAgora.add(estadoVazio(VaadinIcon.CLOCK, "Nenhuma aula com checkin aberto neste momento.", detalhe));
        } else {
            Div grelha = new Div();
            grelha.addClassName("cf-kiosk-now-grid");
            for (AulaHojeKiosk a : agoraLista) {
                grelha.add(criarCartaoAgora(a, agora));
            }
            secAgora.add(grelha);
        }
        conteudo.add(secAgora);

        // A seguir
        if (!seguir.isEmpty()) {
            Div secSeguir = seccao("A seguir hoje", String.valueOf(seguir.size()), false);
            Div lista = new Div();
            lista.addClassName("cf-kiosk-next-list");
            for (AulaHojeKiosk a : seguir) {
                lista.add(criarLinhaSeguir(a, agora));
            }
            secSeguir.add(lista);
            conteudo.add(secSeguir);
        }

        Paragraph rodape = new Paragraph("Esta página atualiza-se automaticamente.");
        rodape.addClassName("cf-kiosk-footnote");
        conteudo.add(rodape);
    }

    private Div seccao(String titulo, String contagem, boolean aoVivo) {
        Div sec = new Div();
        sec.addClassName("cf-kiosk-section");
        H2 h = new H2();
        h.addClassName("cf-kiosk-section-title");
        if (aoVivo) {
            Span ponto = new Span();
            ponto.addClassName("cf-kiosk-live-dot");
            ponto.getElement().setAttribute("aria-hidden", "true");
            h.add(ponto);
        }
        h.add(new Span(titulo));
        if (contagem != null) {
            Span c = new Span(contagem);
            c.addClassName("cf-kiosk-section-count");
            h.add(c);
        }
        sec.add(h);
        return sec;
    }

    private Component criarCartaoAgora(AulaHojeKiosk a, LocalDateTime agora) {
        boolean aberto = a.checkinAberto(agora);

        NativeButton card = new NativeButton();
        card.addClassName("cf-kiosk-class-card");
        aplicarCorTurma(card, a.turma);

        Div topo = new Div();
        topo.addClassName("cf-kiosk-card-top");
        Span estado = new Span();
        estado.addClassName("cf-kiosk-status");
        if (aberto) {
            estado.addClassName("cf-kiosk-status--open");
            estado.add(VaadinIcon.CHECK_CIRCLE.create(), new Span(a.aDecorrer(agora) ? "A decorrer · checkin aberto" : "Checkin aberto"));
        } else {
            estado.addClassName("cf-kiosk-status--running");
            estado.add(VaadinIcon.PLAY_CIRCLE.create(), new Span("A decorrer · checkin fechado"));
        }
        Span horario = new Span(textoHorario(a));
        horario.addClassName("cf-kiosk-card-time");
        topo.add(estado, horario);

        Span nome = new Span(a.turma.getDescricao());
        nome.addClassName("cf-kiosk-card-title");

        card.add(topo, nome, criarMeta(a));

        Div fundo = new Div();
        fundo.addClassName("cf-kiosk-card-bottom");
        Span detalhe = new Span(textoEstadoAgora(a, agora));
        detalhe.addClassName("cf-kiosk-card-detail");
        fundo.add(detalhe);
        if (aberto) {
            Span cta = new Span("Tocar para entrar");
            cta.addClassName("cf-kiosk-card-cta");
            cta.add(VaadinIcon.ARROW_RIGHT.create());
            fundo.add(cta);
            card.addClickListener(e -> mostrarRoster(a));
        } else {
            card.addClassName("cf-kiosk-class-card--closed");
            card.setEnabled(false);
        }
        card.add(fundo);
        return card;
    }

    private Component criarLinhaSeguir(AulaHojeKiosk a, LocalDateTime agora) {
        Div linha = new Div();
        linha.addClassName("cf-kiosk-next-item");
        aplicarCorTurma(linha, a.turma);

        Div hora = new Div();
        hora.addClassName("cf-kiosk-next-time");
        Span ini = new Span(a.inicio.format(HORA));
        ini.addClassName("cf-kiosk-next-start");
        hora.add(ini);
        if (a.temHoraFim) {
            Span fim = new Span("até " + a.fim.format(HORA));
            fim.addClassName("cf-kiosk-next-end");
            hora.add(fim);
        }

        Div info = new Div();
        info.addClassName("cf-kiosk-next-info");
        Span nome = new Span(a.turma.getDescricao());
        nome.addClassName("cf-kiosk-next-title");
        info.add(nome, criarMeta(a));

        Span contagem = new Span();
        contagem.addClassName("cf-kiosk-countdown");
        contagem.add(VaadinIcon.HOURGLASS.create(), new Span("Checkin abre em " + textoContagem(agora, a.checkinAbre)));

        linha.add(hora, info, contagem);
        return linha;
    }

    private Div criarMeta(AulaHojeKiosk a) {
        Div meta = new Div();
        meta.addClassName("cf-kiosk-meta");
        if (a.professorNome != null && !a.professorNome.isBlank()) {
            meta.add(itemMeta(VaadinIcon.USER, a.professorNome));
        }
        if (a.modalidadeNome != null && !a.modalidadeNome.isBlank()
                && !a.modalidadeNome.equalsIgnoreCase(a.turma.getDescricao())) {
            meta.add(itemMeta(VaadinIcon.MUSIC, a.modalidadeNome));
        }
        if (a.salaNome != null && !a.salaNome.isBlank()) {
            meta.add(itemMeta(VaadinIcon.MAP_MARKER, a.salaNome));
        }
        return meta;
    }

    private static Span itemMeta(VaadinIcon icone, String texto) {
        Span s = new Span();
        s.addClassName("cf-kiosk-meta-item");
        Icon i = icone.create();
        i.getElement().setAttribute("aria-hidden", "true");
        s.add(i, new Span(texto));
        return s;
    }

    /** Cor da turma como acento lateral (dado dinâmico — única variável inline). */
    private static void aplicarCorTurma(Component c, Turma turma) {
        String cor = turma.getCor();
        if (cor != null && cor.matches("#[0-9a-fA-F]{3,8}")) {
            c.getElement().getStyle().set("--cf-kiosk-accent", cor);
        }
    }

    private Component estadoVazio(VaadinIcon icone, String titulo, String detalhe) {
        Div vazio = new Div();
        vazio.addClassName("cf-kiosk-empty");
        Icon i = icone.create();
        i.addClassName("cf-kiosk-empty-icon");
        Span t = new Span(titulo);
        t.addClassName("cf-kiosk-empty-title");
        vazio.add(i, t);
        if (detalhe != null) {
            Span d = new Span(detalhe);
            d.addClassName("cf-kiosk-empty-detail");
            vazio.add(d);
        }
        return vazio;
    }

    // ------------------------------------------------------------------
    // Roster
    // ------------------------------------------------------------------

    private void mostrarRoster(AulaHojeKiosk aula) {
        turmaAberta = aula.turma;
        aulaAberta = aula;
        chaveListaAtual = null;
        conteudo.removeAll();

        Turma turma = aula.turma;
        LocalDate hoje = LocalDate.now();

        Set<Aluno> roster = new LinkedHashSet<>();
        List<Aluno> matriculados = turmaService.getAlunosDaTurma(turma);
        roster.addAll(matriculados);
        roster.addAll(checkinService.listarAvulsosElegiveisParaTurma(turma));
        Set<Long> idsMatriculados = new HashSet<>();
        for (Aluno m : matriculados) {
            if (m.getId() != null) idsMatriculados.add(m.getId());
        }

        List<Aluno> ordenado = roster.stream()
                .sorted(Comparator.comparing(Aluno::getNomeCompleto, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        // Presenças de hoje carregadas uma vez (cada cartão usa a sua).
        List<Presenca> presencas = new ArrayList<>();
        int presentes = 0;
        for (Aluno aluno : ordenado) {
            Presenca p = presencaRepository.findByAlunoAndTurmaAndData(aluno, turma, hoje).orElse(null);
            presencas.add(p);
            if (p != null && p.isPresente()) presentes++;
        }

        // Cabeçalho do roster
        Div cab = new Div();
        cab.addClassName("cf-kiosk-roster-header");
        aplicarCorTurma(cab, turma);

        NativeButton voltar = new NativeButton();
        voltar.addClassName("cf-kiosk-back");
        voltar.add(VaadinIcon.ARROW_LEFT.create(), new Span("Aulas"));
        voltar.getElement().setAttribute("aria-label", "Voltar às aulas");
        voltar.addClickListener(e -> mostrarListaTurmas());

        Div info = new Div();
        info.addClassName("cf-kiosk-roster-info");
        H1 titulo = new H1(turma.getDescricao());
        titulo.addClassName("cf-kiosk-roster-title");
        Span horario = new Span(textoHorario(aula));
        horario.addClassName("cf-kiosk-card-time");
        info.add(titulo, horario, criarMeta(aula));

        Div lado = new Div();
        lado.addClassName("cf-kiosk-roster-side");
        Span contador = new Span(presentes + " / " + ordenado.size());
        contador.addClassName("cf-kiosk-roster-count");
        Span contadorLbl = new Span("presentes");
        contadorLbl.addClassName("cf-kiosk-roster-count-label");
        rosterEstado = new Span(textoEstadoAgora(aula, LocalDateTime.now()));
        rosterEstado.addClassName("cf-kiosk-roster-status");
        rosterEstado.setClassName("cf-kiosk-roster-status--closed", !aula.checkinAberto(LocalDateTime.now()));
        lado.add(contador, contadorLbl, rosterEstado);

        cab.add(voltar, info, lado);
        conteudo.add(cab);

        if (ordenado.isEmpty()) {
            conteudo.add(estadoVazio(VaadinIcon.USERS, "Sem alunos nesta turma.",
                    "Não há alunos matriculados nem com crédito válido para esta turma."));
            return;
        }

        Paragraph ajuda = new Paragraph("Toca no teu nome para marcar presença. Tocar de novo anula.");
        ajuda.addClassName("cf-kiosk-roster-help");
        conteudo.add(ajuda);

        Div grelha = new Div();
        grelha.addClassName("cf-kiosk-roster-grid");
        for (int i = 0; i < ordenado.size(); i++) {
            Aluno aluno = ordenado.get(i);
            boolean avulso = aluno.getId() == null || !idsMatriculados.contains(aluno.getId());
            grelha.add(criarCardAluno(aluno, aula, presencas.get(i), avulso));
        }
        conteudo.add(grelha);
    }

    private Component criarCardAluno(Aluno aluno, AulaHojeKiosk aula, Presenca existente, boolean avulso) {
        Turma turma = aula.turma;
        boolean presente = existente != null && existente.isPresente();

        NativeButton card = new NativeButton();
        card.addClassName("cf-kiosk-student");
        if (presente) card.addClassName("cf-kiosk-student--present");
        card.getElement().setAttribute("aria-pressed", String.valueOf(presente));

        Div avatar = new Div();
        avatar.addClassName("cf-kiosk-avatar");
        if (aluno.getFotoChave() != null && !aluno.getFotoChave().isBlank()) {
            Image image = new Image(storageService.gerarUrlTemporario(aluno.getFotoChave(), Duration.ofHours(2)),
                    "Foto de " + aluno.getNomeCompleto());
            avatar.add(image);
        } else {
            Span iniciais = new Span(iniciais(aluno.getNomeCompleto()));
            iniciais.addClassName("cf-kiosk-avatar-initials");
            avatar.add(iniciais);
        }
        if (presente) {
            Span visto = new Span();
            visto.addClassName("cf-kiosk-avatar-check");
            visto.add(VaadinIcon.CHECK.create());
            avatar.add(visto);
        }

        Span nome = new Span(aluno.getNomeCompleto());
        nome.addClassName("cf-kiosk-student-name");

        Span estado = new Span(presente ? "Presente" : "Marcar presença");
        estado.addClassName("cf-kiosk-student-state");

        card.add(avatar, nome, estado);
        if (avulso) {
            Span tag = new Span("Aula avulsa");
            tag.addClassName("cf-kiosk-student-tag");
            card.add(tag);
        }

        card.addClickListener(e -> {
            try {
                if (presente && existente != null) {
                    checkinService.cancelarCheckin(existente);
                    notificar("Presença anulada: " + primeiroNome(aluno.getNomeCompleto()), false);
                } else {
                    checkinService.registarCheckin(aluno, turma, studioAtual(), MetodoRegistoPresenca.KIOSK_PRESENCA);
                    notificar("Bem-vindo(a), " + primeiroNome(aluno.getNomeCompleto()) + "! Presença registada.", true);
                }
                mostrarRoster(aula);
            } catch (IllegalStateException ex) {
                Notification n = Notification.show(ex.getMessage(), 4000, Notification.Position.MIDDLE);
                n.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        return card;
    }

    private static void notificar(String texto, boolean sucesso) {
        Notification n = Notification.show(texto, 2500, Notification.Position.MIDDLE);
        n.addThemeVariants(sucesso ? NotificationVariant.LUMO_SUCCESS : NotificationVariant.LUMO_CONTRAST);
    }

    // ------------------------------------------------------------------
    // Textos
    // ------------------------------------------------------------------

    private static String saudacao(LocalDateTime agora) {
        int h = agora.getHour();
        if (h >= 6 && h < 12) return "Bom dia";
        if (h >= 12 && h < 20) return "Boa tarde";
        return "Boa noite";
    }

    private static String textoHorario(AulaHojeKiosk a) {
        return a.temHoraFim ? a.inicio.format(HORA) + "–" + a.fim.format(HORA) : "Início às " + a.inicio.format(HORA);
    }

    /** Linha de estado para uma aula na secção "agora" (ou no cabeçalho do roster). */
    private static String textoEstadoAgora(AulaHojeKiosk a, LocalDateTime agora) {
        if (a.checkinAberto(agora)) {
            if (agora.isBefore(a.inicio)) {
                return "Começa em " + textoContagem(agora, a.inicio) + " · checkin até às " + a.checkinFecha.format(HORA);
            }
            return "Checkin fecha em " + textoContagem(agora, a.checkinFecha);
        }
        if (agora.isBefore(a.checkinAbre)) {
            return "Checkin abre em " + textoContagem(agora, a.checkinAbre);
        }
        if (a.aDecorrer(agora)) {
            return "Termina às " + a.fim.format(HORA);
        }
        return "Checkin fechado";
    }

    /** "1 min", "12 min", "1h", "1h 05min" — arredondado para cima ao minuto. */
    private static String textoContagem(LocalDateTime agora, LocalDateTime alvo) {
        long seg = Math.max(0, Duration.between(agora, alvo).getSeconds());
        long min = Math.max(1, (seg + 59) / 60);
        if (min < 60) return min + " min";
        long h = min / 60;
        long m = min % 60;
        return m == 0 ? h + "h" : String.format("%dh %02dmin", h, m);
    }

    private static String idAula(AulaHojeKiosk a) {
        return a.turma.getId() + "@" + a.inicio.toLocalTime();
    }

    private static String capitalizar(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) return "";
        return nome.trim().split("\\s+")[0];
    }

    private static String iniciais(String nome) {
        if (nome == null || nome.isBlank()) return "?";
        String[] partes = nome.trim().split("\\s+");
        String r = partes[0].substring(0, 1);
        if (partes.length > 1) r += partes[partes.length - 1].substring(0, 1);
        return r.toUpperCase(PT);
    }
}
