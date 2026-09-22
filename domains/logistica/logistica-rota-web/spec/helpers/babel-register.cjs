// Faz o Jasmine (que só entende JS puro) entender JSX/ES modules nos specs e no src/,
// transpilando com o mesmo babel.config.cjs usado pelo Vite. Necessário porque, ao
// contrário do Jest, o Jasmine não embute nenhum transform de JSX.
require("@babel/register")({
  extensions: [".js", ".jsx"],
  presets: [
    ["@babel/preset-env", { targets: { node: "current" }, modules: "commonjs" }],
    ["@babel/preset-react", { runtime: "automatic" }]
  ],
  ignore: [/node_modules/]
});
