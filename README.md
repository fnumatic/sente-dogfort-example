# Node.js Sente reference example

> This example dives into Sente's full functionality pretty quickly; it's probably more useful as a reference than a tutorial. Please see Sente's top-level README for a gentler intro.

## Sente, DogFort, Figwheel, Om Next
 * This example utilizes [Figwheel](http://github.com/bhauman/lein-figwheel) for instant reload on code change for server and client. 
 * [Om Next](http://github.com/omcljs/om) is used for the client. 
 * [DogFort](http://github.com/whamtet/dogfort) is used for the server.

## Standing on the shoulders of giants
 Example derived from [here](http://github.com/theasp/sente-nodejs-example)

## Instructions

  1. Call `lein start` at your terminal, then point your browser at http://localhost:4000
  2. Observe std-out (server log) and web page textarea (client log)
  
  Or
  
  1. Call `lein fw` at one terminal, after few seconds call `lein node` on other terminal. Then point your browser at http://localhost:4000