package pt.studioflow.service;

import org.springframework.stereotype.Service;
import pt.studioflow.model.Idioma;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Traduções PT/EN/FR dos formulários públicos (inscrição e renovação). Não
 * usa o mecanismo de i18n do Spring (MessageSource) porque o projeto não o
 * usa em mais nenhum sítio — um mapa simples chave→idioma é suficiente para
 * o número reduzido de strings destes formulários e mantém-se fácil de
 * estender com novas chaves.
 */
@Service
public class TranslationService {

    private static final Map<String, Map<Idioma, String>> TRADUCOES = new HashMap<>();

    private static void add(String key, String pt, String en, String fr) {
        Map<Idioma, String> m = new EnumMap<>(Idioma.class);
        m.put(Idioma.PT, pt);
        m.put(Idioma.EN, en);
        m.put(Idioma.FR, fr);
        TRADUCOES.put(key, m);
    }

    static {
        // Ficha de Inscrição
        add("inscricao.titulo", "Ficha de Inscrição", "Registration Form", "Fiche d'Inscription");
        add("inscricao.subtitulo", "Junte-se à nossa família de dança!", "Join our dance family!",
                "Rejoignez notre famille de danse !");
        add("inscricao.nomeCompleto", "Nome Completo", "Full Name", "Nom Complet");
        add("inscricao.dataNascimento", "Data de Nascimento", "Date of Birth", "Date de Naissance");
        add("inscricao.telemovel", "Telemóvel", "Phone", "Téléphone");
        add("inscricao.email", "E-mail", "Email", "E-mail");
        add("inscricao.morada", "Morada Completa", "Full Address", "Adresse Complète");
        add("inscricao.turmas", "Escolha as Turmas", "Choose Your Classes", "Choisissez vos Cours");
        add("inscricao.submeter", "Finalizar Inscrição", "Submit Registration", "Finaliser l'Inscription");
        add("inscricao.sucesso.titulo", "Enviado!", "Sent!", "Envoyé !");
        add("inscricao.sucesso.msg", " entrará em contacto muito em breve.",
                " will contact you very soon.", " vous contactera très bientôt.");

        // Renovação de Matrícula
        add("renovacao.titulo", "Renovação de Matrícula", "Enrollment Renewal", "Renouvellement d'Inscription");
        add("renovacao.subtitulo", "Continua connosco! Indica o teu email para começar.",
                "Stay with us! Enter your email to begin.", "Reste avec nous ! Indique ton email pour commencer.");
        add("renovacao.email", "O teu email", "Your email", "Ton email");
        add("renovacao.procurar", "Procurar", "Search", "Rechercher");
        add("renovacao.turmas", "Turmas", "Classes", "Cours");
        add("renovacao.submeter", "Submeter Pedido de Renovação", "Submit Renewal Request", "Soumettre la Demande");
        add("renovacao.sucesso.titulo", "Pedido enviado!", "Request sent!", "Demande envoyée !");
        add("renovacao.sucesso.msg", " entrará em contacto muito em breve.",
                " will contact you very soon.", " vous contactera très bientôt.");
    }

    public String t(String key, Idioma idioma) {
        Map<Idioma, String> m = TRADUCOES.get(key);
        if (m == null) return key;
        Idioma alvo = idioma != null ? idioma : Idioma.PT;
        return m.getOrDefault(alvo, m.get(Idioma.PT));
    }
}
