import { toQuery, toSort } from './document-grid-query';

describe('grid query mapping', () => {
  it('falls back to the API default sort when no accepted column is sorted', () => {
    expect(toSort()).toBe('updatedAt:desc');
    expect(toSort([])).toBe('updatedAt:desc');
    expect(toSort([{ colId: 'unknownColumn', sort: 'asc' }])).toBe('updatedAt:desc');
  });

  it('translates the first sorted column, ascending or descending', () => {
    expect(toSort([{ colId: 'title', sort: 'asc' }])).toBe('title:asc');
    expect(toSort([{ colId: 'version', sort: 'desc' }])).toBe('version:desc');
    expect(
      toSort([
        { colId: 'status', sort: 'asc' },
        { colId: 'title', sort: 'desc' },
      ]),
    ).toBe('status:asc');
  });

  it('builds the exclusive window and omits blank filters', () => {
    expect(toQuery({ startRow: 0, endRow: 25, sortModel: [] }, {})).toEqual({
      startRow: 0,
      endRow: 25,
      sort: 'updatedAt:desc',
      status: undefined,
      q: undefined,
    });
    expect(
      toQuery({ startRow: 50, endRow: 75, sortModel: [] }, { status: 'Rejected', q: '  ' }),
    ).toEqual({
      startRow: 50,
      endRow: 75,
      sort: 'updatedAt:desc',
      status: 'Rejected',
      q: undefined,
    });
    expect(toQuery({ startRow: 0, endRow: 25, sortModel: [] }, { q: ' contrat ' }).q).toBe(
      'contrat',
    );
  });
});
