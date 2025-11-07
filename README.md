## Funcionalidades

* Detecta bolhas preenchidas e não preenchidas em imagens de formulários.
* Destaca as bolhas preenchidas em verde e as não preenchidas em vermelho.
* Calcula a intensidade média das bolhas marcadas e não marcadas.
* Gera arquivo com respostas interpretadas.
* Suporta múltiplas folhas e múltiplas questões por folha.

## Pré-requisitos

* Java 11 ou superior
* OpenCV (versão compatível com Java, ex: 4.1.2)
* IDE recomendada: NetBeans, IntelliJ ou Eclipse

## Estrutura do Projeto

```
questDetector/
│
├─ src/                     # Código-fonte Java
│   └─ questdetector/
│       └─ questDetector.java
│
├─ testes/                  # Arquivos de teste
│   ├─ entradas/            # Imagens dos formulários
│   └─ config.txt           # Configurações das coordenadas das alternativas
│
├─ README.md                # Este arquivo
└─ .gitignore               # Para ignorar arquivos binários e temporários
```

> **Observação:** DLLs ou bibliotecas do OpenCV não devem ser versionadas. Cada usuário deve instalar o OpenCV em sua máquina.

## Configuração do OpenCV

1. Instale o OpenCV em sua máquina.
2. Aponte a DLL no código (Windows) ou lib (Linux/Mac):

```java
System.load("C:\\caminho\\para\\opencv_java4120.dll");
```

3. Certifique-se de que o caminho para as imagens de entrada e o arquivo `config.txt` está correto no código:

```java
String caminhoConfig = "C:\\Users\\usuario\\opencv\\testes\\config.txt";
String caminhoImagem = "C:\\Users\\usuario\\opencv\\testes\\entradas\\entrada_folha1.jpg";
```

## Como executar

1. Compile o projeto na IDE ou via terminal:

```bash
javac -cp .;path/to/opencv/build/java/opencv-412.jar src/questdetector/questDetector.java
```

2. Execute o projeto:

```bash
java -cp .;path/to/opencv/build/java/opencv-412.jar questdetector.questDetector
```

3. Os resultados serão gerados em:

* `testes/resultado_<folha>.jpg` → imagens com quadrados destacados
* `testes/respostas_brutas.txt` → respostas detectadas

## Observações

* Ajuste o valor de intensidade na linha do código que define se uma bolha está marcada:

```java
boolean marcada = media.val[0] < 200; // Pode ser ajustado conforme a imagem
```

* O projeto suporta qualquer número de folhas e alternativas, desde que estejam listadas no `config.txt`.


