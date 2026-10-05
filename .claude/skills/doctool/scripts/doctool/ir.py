"""doctool IR — 中間表現(doctool-ir)の共通定義。
"""

IR_FORMAT = 'doctool-ir'
IR_VERSION = '0.1'


def prune(d):
    """null・空配列・空辞書のキーを落とす(text の空文字は保持する)。"""
    return {k: v for k, v in d.items() if v is not None and v != [] and v != {}}
