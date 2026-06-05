function renderBarChart(canvasId, labels, values) {
    const ctx = document.getElementById(canvasId).getContext('2d');
    if (!ctx) {
        console.error("Failed to get context for canvas:", canvasId);
        return;
    }

    new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [{
                label: 'Número de Alunos',
                data: values,
                backgroundColor: 'rgba(54, 162, 235, 0.6)',
                borderColor: 'rgba(54, 162, 235, 1)',
                borderWidth: 1
            }]
        },
        options: {
            responsive: true,
            scales: {
                y: { beginAtZero: true }
            }
        }
    });
}