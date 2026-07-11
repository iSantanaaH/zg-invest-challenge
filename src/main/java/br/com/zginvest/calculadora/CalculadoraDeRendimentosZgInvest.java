package br.com.zginvest.calculadora;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CalculadoraDeRendimentosZgInvest {

    private final List<Negociacao> negociacoes;
    private final Map<LocalDate, BigDecimal> precosFechamento;

    public CalculadoraDeRendimentosZgInvest(List<Negociacao> negociacoes, Map<LocalDate, BigDecimal> precosFechamento) {
        this.negociacoes = negociacoes.stream()
                .sorted(Comparator.comparing(Negociacao::data))
                .collect(Collectors.toList());
        this.precosFechamento = precosFechamento;
    }

    public PosicaoCarteira calcularPosicaoEm(LocalDate data) {
        if (!precosFechamento.containsKey(data)) {
            throw new IllegalArgumentException(
                    "Não há preço de fechamento cadastrado para a data: " + data);
        }

        BigDecimal precoFechamento = precosFechamento.get(data);

        long posicao = 0;
        BigDecimal custoMedio = BigDecimal.ZERO;
        BigDecimal acumuladorCusto = BigDecimal.ZERO;

        for (Negociacao negociacao : negociacoes) {
            if (negociacao.data().isAfter(data)) {
                continue;
            }

            long qty = negociacao.quantidade();
            BigDecimal preco = negociacao.preco();
            boolean isCompra = negociacao.tipo() == OperacaoNegociacao.COMPRA; 
            long delta = isCompra ? qty : -qty;
            long novaPosicao = posicao + delta;

            if (posicao == 0) {
                posicao = novaPosicao;
                acumuladorCusto = preco.multiply(BigDecimal.valueOf(qty));
                custoMedio = preco.setScale(2, RoundingMode.HALF_UP);

            } else if (Long.signum(novaPosicao) == Long.signum(posicao) || novaPosicao == 0) {
                // Se a operação mantém ou aumenta o sinal atual, estamos ADICIONANDO posição.
                // Exemplo Long: posição > 0 e vem COMPRA.
                // Exemplo Short: posição < 0 e vem VENDA.
                boolean adicionandoPosicao = (posicao > 0 && isCompra) || (posicao < 0 && !isCompra);

                if (adicionandoPosicao) {
                    acumuladorCusto = acumuladorCusto.add(preco.multiply(BigDecimal.valueOf(qty)));
                    custoMedio = acumuladorCusto.divide(BigDecimal.valueOf(Math.abs(novaPosicao)), 2, RoundingMode.HALF_UP);
                } else {
                    // Reduzindo posição (Venda parcial no Long ou Recompra parcial no Short): 
                    // O preço médio NÃO muda. Apenas atualiza o acumulador proporcionalmente.
                    acumuladorCusto = custoMedio.multiply(BigDecimal.valueOf(Math.abs(novaPosicao)));
                }

                posicao = novaPosicao;

            } else {
                // Virada de mão (Inversão direta de Long para Short ou vice-versa)
                long qtdNovaPosicao = Math.abs(novaPosicao);
                posicao = novaPosicao;
                acumuladorCusto = preco.multiply(BigDecimal.valueOf(qtdNovaPosicao));
                custoMedio = preco.setScale(2, RoundingMode.HALF_UP);
            }
        }

        // Cálculo do saldo financeiro atualizado da carteira para o ativo
        BigDecimal saldoAtual;
        BigDecimal rendimentoPercentual;

        if (posicao == 0 || custoMedio.compareTo(BigDecimal.ZERO) == 0) {
            saldoAtual = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            rendimentoPercentual = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            // Saldo absoluto das ações sob custódia/compromisso avaliado a mercado
            saldoAtual = precoFechamento
                    .multiply(BigDecimal.valueOf(Math.abs(posicao)))
                    .setScale(2, RoundingMode.HALF_UP);

            if (posicao > 0) {
                // Lógica de Long (Lucra na alta: Preço Fechamento > Custo Médio)
                rendimentoPercentual = precoFechamento
                        .subtract(custoMedio)
                        .divide(custoMedio, 10, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, RoundingMode.DOWN); // Garante o 32.05% e 44.05% da planilha deles
            } else {
                // Lógica de Short (Lucra na queda: Custo Médio de Venda > Preço Fechamento)
                rendimentoPercentual = custoMedio
                        .subtract(precoFechamento)
                        .divide(custoMedio, 10, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, RoundingMode.DOWN);
            }
        }

        return new PosicaoCarteira(data, posicao, saldoAtual, rendimentoPercentual);
    }
}