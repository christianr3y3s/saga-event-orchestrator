// Jasmine (rodando puro em Node) não tem DOM nenhum -- ao contrário do Jest, que já sobe
// jsdom por padrão. jsdom-global injeta window/document/navigator no escopo global do
// processo, o suficiente para @testing-library/react funcionar dentro dos specs.
require("jsdom-global")();
