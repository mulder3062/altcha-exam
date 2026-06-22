// Altcha 위젯은 Web Component(<altcha-widget>)이므로 JSX 타입을 선언해 둔다.
import 'react';

declare module 'react' {
  namespace JSX {
    interface IntrinsicElements {
      'altcha-widget': any;
    }
  }
}
