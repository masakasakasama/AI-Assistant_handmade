import test from 'node:test';
import assert from 'node:assert/strict';
import { validateNewsBatch, newsApiError } from '../src/news-store.mjs';
import handler from '../../api/news.mjs';

const sample = {
  batchId:'a'.repeat(64), capturedAt:'2026-10-18T00:00:00+00:00',
  headlineCount:3, digest:'重大ニュース3件を確認',
  alerts:[{score:85,source:'world-news',summary:'重要な動向',url:'https://example.com/article'}]
};

test('news batch validates structured alerts', () => {
  assert.equal(validateNewsBatch(sample).alerts[0].score, 85);
  for (const invalid of [
    {...sample,batchId:'../../etc/passwd'}, {...sample,headlineCount:999},
    {...sample,alerts:[{...sample.alerts[0],url:'javascript:alert(1)'}]},
    {...sample,alerts:[{...sample.alerts[0],score:2}]},
    {...sample,alerts:[{...sample.alerts[0],source:'abc<>bad'}]}
  ]) assert.throws(()=>validateNewsBatch(invalid));
  assert.equal(newsApiError(new Error('not an exposed database error')).status,503);
});

test('news endpoint rejects missing/wrong credentials without touching database',async()=>{
  const old=process.env.AI_BACKEND_TOKEN;
  try {
    process.env.AI_BACKEND_TOKEN='owner-test-token';
    for (const method of ['GET','POST']) {
      for (const auth of [undefined,'Bearer wrong']) {
        const req={method, headers:{authorization:auth},get body(){throw new Error('should not read request body')}};
        const res={setHeader(){},status(code){this.code=code;return this;},json(v){this.body=v;return this;}};
        await handler(req,res);
        assert.equal(res.code,401);
      }
    }
  } finally {
    if (old===undefined) delete process.env.AI_BACKEND_TOKEN;
    else process.env.AI_BACKEND_TOKEN=old;
  }
});
