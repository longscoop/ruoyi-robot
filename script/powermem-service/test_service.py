import json
import threading
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
import pytest
from service import MemoryService, handler_for, expand_environment

NS = 'tenant-1-agent-1-robot-2'
class FakeMemory:
    def __init__(self):
        self.saved=[]
        self.deleted=[]
    def search(self, query, user_id, limit):
        assert user_id == NS
        return {'results': [
            {'id':1,'user_id':NS,'memory':'用户喜欢咖啡','metadata':{}},
            {'id':2,'user_id':NS,'memory':'过时情景','metadata':{'memory_management':{'should_forget':True}}},
            {'id':3,'user_id':'other','memory':'另一个人的资料','metadata':{}}]}
    def profile(self, user_id):
        return {'topics':{'work':{'facts':'程序员'}}}
    def add(self, **data):
        self.saved.append(data)
    def delete(self, memory_id, **data):
        self.deleted.append((memory_id,data))

def test_scoped_query_excludes_profile_and_filters_forgotten_and_foreign_rows():
    result=MemoryService(FakeMemory()).execute('/query', {'namespace':NS, 'query':'我喜欢什么', 'limit':8})
    assert len(result['results'])==1
    assert 'profile' not in result

def test_save_extracts_user_profile_and_rejects_assistant_claims():
    memory=FakeMemory(); service=MemoryService(memory)
    service.execute('/save', {'namespace':NS, 'messages':[{'role':'user','content':'我喜欢咖啡'}]})
    assert memory.saved[0]['user_id']==NS
    assert memory.saved[0]['include_roles']==['user']
    assert memory.saved[0]['infer'] is True
    assert 'preferences' in memory.saved[0]['custom_topics']
    with pytest.raises(ValueError):
        service.execute('/save', {'namespace':NS, 'messages':[{'role':'assistant','content':'你住上海'}]})

def test_forget_deletes_only_exact_authorized_match_and_invalid_namespace_rejected():
    memory=FakeMemory();service=MemoryService(memory)
    assert service.execute('/forget', {'namespace':NS,'content':'用户喜欢咖啡'})['deleted']==1
    assert memory.deleted==[(1,{'user_id':NS,'delete_profile':True})]
    assert service.execute('/forget', {'namespace':NS,'content':'咖啡'})['deleted']==0
    with pytest.raises(ValueError):service.execute('/query',{'namespace':'../../other','query':'工作'})

def test_http_auth_and_sanitized_errors():
    server=ThreadingHTTPServer(('127.0.0.1',0), handler_for(MemoryService(FakeMemory()),'private-token'))
    thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
    try:
        url=f'http://127.0.0.1:{server.server_port}/query'
        data=json.dumps({'namespace':NS,'query':'工作','limit':8}).encode()
        with pytest.raises(urllib.error.HTTPError) as error:
            urllib.request.urlopen(urllib.request.Request(url,data=data),timeout=2)
        assert error.value.code==401
        request=urllib.request.Request(url,data=data,headers={'Authorization':'Bearer private-token'})
        assert len(json.load(urllib.request.urlopen(request,timeout=2))['results'])==1
    finally:
        server.shutdown();server.server_close()

def test_environment_expansion_never_leaves_unresolved_secret(monkeypatch):
    monkeypatch.setenv('TEST_LLM_KEY','test')
    assert expand_environment({'key':'${TEST_LLM_KEY}'})=={'key':'test'}
    with pytest.raises(ValueError):expand_environment('${MISSING_TEST_KEY_7813}')

def test_query_never_waits_for_slow_save_and_cache_is_invalidated_before_write():
    import time
    entered, release = threading.Event(), threading.Event()
    class SlowMemory(FakeMemory):
        def add(self, **data):
            entered.set()
            release.wait(3)
            super().add(**data)
    service = MemoryService(SlowMemory())
    query = {'namespace': NS, 'query': '咖啡', 'limit': 8}
    assert service.execute('/query', query)['results']
    thread = threading.Thread(target=lambda: service.execute('/save',
        {'namespace': NS, 'messages': [{'role': 'user', 'content': '现在不喝咖啡'}]}))
    thread.start()
    try:
        assert entered.wait(1)
        start = time.monotonic()
        assert service.execute('/query', query) == {'results': [], 'busy': True}
        assert time.monotonic() - start < .1
    finally:
        release.set()
        thread.join(2)


def test_busy_cache_is_exact_query_and_namespace_scoped():
    service = MemoryService(FakeMemory())
    query = {'namespace': NS, 'query': '咖啡', 'limit': 8}
    expected = service.execute('/query', query)
    with service.lock:
        assert service.execute('/query', query) == expected
        assert not service.execute('/query', {**query, 'query': '天气'})['results']
        assert not service.execute('/query', {**query, 'namespace': 'tenant-2-agent-1-robot-2'})['results']
